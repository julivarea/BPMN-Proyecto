package ar.edu.unrc.gtpn.web.solicitudes;

import java.util.Map;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;

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

    private final SolicitudesRepository repositorio;
    private final ClienteFlowable flowable;
    private final SolicitudesProperties propiedades;

    public SolicitudesDeCapacitacion(SolicitudesRepository repositorio, ClienteFlowable flowable,
            SolicitudesProperties propiedades) {
        this.repositorio = repositorio;
        this.flowable = flowable;
        this.propiedades = propiedades;
    }

    /** Devuelve el id del curso creado, que es también la businessKey de su instancia. */
    @Transactional
    public long solicitar(SolicitudFormulario solicitud) {
        String cuit = soloDigitos(solicitud.cuit());
        long empresaId = empresaSolicitante(cuit, solicitud.razonSocial().strip());
        long cursoId = repositorio.crearCurso(empresaId, solicitud);
        String instanciaId = iniciarProceso(cursoId, solicitud.nombre().strip());
        repositorio.vincularInstancia(cursoId, instanciaId);
        return cursoId;
    }

    private String iniciarProceso(long cursoId, String nombreCurso) {
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