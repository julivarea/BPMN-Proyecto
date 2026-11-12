package ar.edu.unrc.gtpn.web.solicitudes;

import java.util.Map;
import java.util.Optional;

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
        String instanciaId = iniciarProceso(cursoId);
        repositorio.vincularInstancia(cursoId, instanciaId);
        return cursoId;
    }

    private String iniciarProceso(long cursoId) {
        try {
            return flowable.iniciarPorMensaje(MENSAJE_DE_INICIO, String.valueOf(cursoId),
                    Map.of(PLAZO_RESPUESTA, propiedades.plazoRespuesta()));
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
}