package ar.edu.unrc.gtpn.web.solicitudes;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registra la solicitud de una empresa en la base de la institución (enunciado §4.1.1, W-01, W-02). */
@Service
public class SolicitudesDeCapacitacion {

    private final SolicitudesRepository repositorio;

    public SolicitudesDeCapacitacion(SolicitudesRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Devuelve el id del curso creado. */
    @Transactional
    public long solicitar(SolicitudFormulario solicitud) {
        String cuit = soloDigitos(solicitud.cuit());
        long empresaId = empresaSolicitante(cuit, solicitud.razonSocial().strip());
        return repositorio.crearCurso(empresaId, solicitud);
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