package ar.edu.unrc.gtpn.web.tareas;

import java.security.Principal;
import java.util.Set;

import org.springframework.stereotype.Component;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.flowable.Tarea;
import ar.edu.unrc.gtpn.web.solicitudes.Curso;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesRepository;

/**
 * Lo que comparten las pantallas de tarea de la web: a qué sección se vuelve al terminar, la verificación de que la
 * tarea es la de la pantalla y del usuario logueado, y el curso que está detrás de la tarea (docs/08-web.md §3).
 */
@Component
public class PantallasDeTareas {

    static final String ADMINISTRACION = "administracion";
    static final String DOCENTES = "docentes";

    private static final Set<String> SECCIONES_VALIDAS = Set.of(ADMINISTRACION, DOCENTES);

    private final ClienteFlowable flowable;
    private final SolicitudesRepository solicitudes;

    public PantallasDeTareas(ClienteFlowable flowable, SolicitudesRepository solicitudes) {
        this.flowable = flowable;
        this.solicitudes = solicitudes;
    }

    /** La sección a la que se vuelve al terminar: la pedida si es una de las del sitio, si no la de la pantalla. */
    String seccion(String pedida, String porDefecto) {
        boolean esDelSitio = pedida != null && SECCIONES_VALIDAS.contains(pedida);
        return esDelSitio ? pedida : porDefecto;
    }

    /**
     * La tarea pendiente, después de verificar que existe, que es la de la pantalla (por su clave en el modelo) y que
     * está asignada al usuario logueado.
     *
     * @param queEs lo que hace la tarea, para el mensaje cuando no es la de la pantalla ("evaluar recursos")
     * @throws AccionNoPermitida si la tarea no existe, es otra o es de otro usuario
     */
    Tarea tareaDelUsuario(String id, String clave, String queEs, Principal usuario) {
        Tarea tarea = flowable.tarea(id);
        if (tarea == null || !clave.equals(tarea.clave())) {
            throw new AccionNoPermitida("La tarea seleccionada no corresponde a " + queEs + ".");
        }
        if (!usuario.getName().equals(tarea.asignado())) {
            throw new AccionNoPermitida("Solo el usuario asignado puede realizar esta tarea.");
        }
        return tarea;
    }

    /** El {@code curso.id} de la solicitud vinculada a la tarea (es la businessKey de su instancia). */
    long cursoIdDe(Tarea tarea) {
        if (tarea.solicitud() == null) {
            throw new AccionNoPermitida("No se pudo recuperar la solicitud vinculada a la tarea.");
        }
        return Long.parseLong(tarea.solicitud());
    }

    /** El curso de la solicitud vinculada a la tarea. */
    Curso cursoDe(Tarea tarea) {
        long cursoId = cursoIdDe(tarea);
        return solicitudes.buscarPorId(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado: " + cursoId));
    }
}
