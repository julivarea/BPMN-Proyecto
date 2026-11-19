package ar.edu.unrc.gtpn.web.solicitudes;

import java.util.Map;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.RestClientException;
import org.springframework.security.core.Authentication;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.flowable.InstanciaIniciada;
import ar.edu.unrc.gtpn.web.seguridad.CuentaEmpresaRepository;

/**
 * Registra la solicitud de una empresa en la base de la institución e inicia su instancia del proceso, vinculada al
 * curso (enunciado §4.1.1, W-01, W-02).
 */
@Service
public class SolicitudesDeCapacitacion {

    // Nombres definidos en el modelo BPMN: cambiarlos rompe el inicio del proceso.
    static final String MENSAJE_DE_INICIO = "solicitudCapacitacion";
    static final String PLAZO_RESPUESTA = "plazoRespuesta";
    // Para mostrar la solicitud por su nombre en la app de comunicaciones, que no accede a la base de la institución.
    static final String NOMBRE_CURSO = "nombreCurso";
    // Rol con el que inicia sesión una empresa (AutenticacionEmpresa).
    static final String ROL_EMPRESA = "ROLE_EMPRESA";

    private final SolicitudesRepository repositorio;
    private final ClienteFlowable flowable;
    private final SolicitudesProperties propiedades;
    private final TransactionOperations transacciones;
    private final CuentaEmpresaRepository cuentas;

    public SolicitudesDeCapacitacion(SolicitudesRepository repositorio, ClienteFlowable flowable,
            SolicitudesProperties propiedades, TransactionOperations transacciones, CuentaEmpresaRepository cuentas) {
        this.repositorio = repositorio;
        this.flowable = flowable;
        this.propiedades = propiedades;
        this.transacciones = transacciones;
        this.cuentas = cuentas;
    }

    /**
     * Devuelve el id del curso creado, que es también la businessKey de su instancia.
     *
     * <p>Si el proceso termina al iniciarse (no viable), la solicitud queda registrada y el resultado lo indica, para que
     * la web se lo informe a quien la envió.
     *
     * <p>El curso se confirma en la base <b>antes</b> de iniciar la instancia: el listener de fin del proceso actualiza
     * el estado del curso, y una instancia que termina al iniciarse (no viable) lo hace antes de que esta solicitud
     * termine. Con todo en una sola transacción, el curso todavía no existiría para el listener y quedaría en curso
     * para siempre. Si Flowable no inicia la instancia, el curso recién creado se elimina.
     */
    public SolicitudRegistrada solicitar(SolicitudFormulario solicitud) {
        return solicitar(solicitud, Optional.empty());
    }

    /** Con una empresa con sesión, la solicitud va a su nombre: se ignoran el CUIT y la razón social del formulario. */
    public SolicitudRegistrada solicitar(SolicitudFormulario solicitud, Optional<Empresa> empresaConSesion) {
        long cursoId = transacciones.execute(estado -> {
            long empresaId = empresaConSesion
                    .map(Empresa::id)
                    .orElseGet(() -> empresaSolicitante(soloDigitos(solicitud.cuit()), solicitud.razonSocial().strip()));
            return repositorio.crearCurso(empresaId, solicitud);
        });

        InstanciaIniciada instancia;
        
        try {
            instancia = iniciarProceso(cursoId, solicitud.nombre().strip());
        } catch (SolicitudNoRegistrada error) {
            repositorio.eliminarCurso(cursoId);
            throw error;
        }
        repositorio.vincularInstancia(cursoId, instancia.id());
        // La única forma de que el proceso termine al iniciarse es que la solicitud no sea viable.
        return new SolicitudRegistrada(cursoId, !instancia.terminada());
    }

    // Empresa con la que inició sesión el usuario, o vacío si no hay sesión o no es una empresa.
    public Optional<Empresa> empresaConSesion(Authentication sesion) {
        boolean esEmpresa = sesion != null && sesion.getAuthorities().stream()
                .anyMatch(permiso -> ROL_EMPRESA.equals(permiso.getAuthority()));
        if (!esEmpresa) {
            return Optional.empty();
        }
        return cuentas.buscarPorUsuario(sesion.getName())
                .flatMap(cuenta -> repositorio.empresaPorId(cuenta.empresaId()));
    }

    private InstanciaIniciada iniciarProceso(long cursoId, String nombreCurso) {
        try {
            return flowable.iniciarPorMensaje(MENSAJE_DE_INICIO, String.valueOf(cursoId),
                    Map.of(PLAZO_RESPUESTA, propiedades.plazoRespuesta(), NOMBRE_CURSO, nombreCurso));
        } catch (RestClientException error) {
            throw new SolicitudNoRegistrada(error);
        }
    }

    private long empresaSolicitante(String cuit, String razonSocial) {
        Optional<Empresa> registrada = repositorio.empresaPorCuit(cuit);
        if (registrada.isEmpty()) {
            return repositorio.crearEmpresa(cuit, razonSocial);
        }
        boolean mismaRazonSocial = registrada.get().razonSocial().equalsIgnoreCase(razonSocial);
        if (!mismaRazonSocial) {
            throw new CuitDeOtraEmpresa();
        }
        return registrada.get().id();
    }

    private static String soloDigitos(String cuit) {
        return cuit.replace("-", "");
    }

        /**
     * Descripción "Razón social · Curso" de cada solicitud, por su businessKey. Las instancias sin curso en la base (por
     * ejemplo, iniciadas desde la UI de Flowable) no aparecen: la bandeja muestra su businessKey.
     */
    public Map<String, String> descripciones(Collection<String> businessKeys) {
        List<Long> cursoIds = businessKeys.stream()
                .filter(SolicitudesDeCapacitacion::esIdDeCurso)
                .map(Long::valueOf)
                .distinct()
                .toList();
        if (cursoIds.isEmpty()) {
            return Map.of();
        }
        return repositorio.descripcionesPorCurso(cursoIds).entrySet().stream()
                .collect(Collectors.toMap(entrada -> String.valueOf(entrada.getKey()), Map.Entry::getValue));
    }

    private static boolean esIdDeCurso(String businessKey) {
        return businessKey != null && businessKey.matches("\\d+");
    }
}