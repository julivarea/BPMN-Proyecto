package ar.edu.unrc.gtpn.web.tareas;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.flowable.Tarea;

/**
 * Reglas para reclamar y completar tareas (enunciado §2, A-06). Flowable no controla quién completa una tarea
 * (05-decisiones, limitación 7), así que toda pantalla de la web que reclame o complete tareas lo hace a través de esta
 * clase, nunca llamando a {@link ClienteFlowable} directamente.
 */
@Service
public class BandejaDeTareas {

    /** Tareas que tienen su propia aplicación y no se completan desde la web (enunciado 4.2.1). */
    static final Set<String> SE_COMPLETAN_FUERA_DE_LA_WEB = Set.of("prepararMateriales");

    private final ClienteFlowable flowable;

    public BandejaDeTareas(ClienteFlowable flowable) {
        this.flowable = flowable;
    }

    public List<Tarea> asignadas(String usuario) {
        return flowable.tareasAsignadas(usuario);
    }

    public List<Tarea> disponibles(String usuario) {
        return flowable.tareasDisponibles(usuario);
    }

    public boolean seCompletaEnLaWeb(Tarea tarea) {
        return !SE_COMPLETAN_FUERA_DE_LA_WEB.contains(tarea.clave());
    }

    /** Asigna al usuario una tarea disponible para sus grupos. */
    public void reclamar(String usuario, String tareaId) {
        boolean disponible = disponibles(usuario).stream().anyMatch(tarea -> tarea.id().equals(tareaId));
        if (!disponible) {
            throw new AccionNoPermitida("La tarea no está disponible para tus grupos.");
        }
        try {
            flowable.reclamar(tareaId, usuario);
        } catch (HttpClientErrorException.Conflict error) {
            throw new AccionNoPermitida("Otro usuario reclamó la tarea antes que vos.");
        }
    }

    /** Completa una tarea asignada al usuario, con los datos que carga la tarea. */
    public void completar(String usuario, String tareaId, Map<String, Object> variables) {
        Tarea tarea = tareaPendiente(tareaId);
        if (!usuario.equals(tarea.asignado())) {
            throw new AccionNoPermitida("Solo el usuario asignado puede completar esta tarea.");
        }
        if (!seCompletaEnLaWeb(tarea)) {
            throw new AccionNoPermitida("Esta tarea se completa desde la app de materiales.");
        }
        try {
            flowable.completar(tareaId, variables);
        } catch (RestClientResponseException error) {
            // Por ejemplo, una tarea que carga datos completada sin ellos: el proceso no puede seguir.
            throw new AccionNoPermitida("Flowable no pudo completar la tarea. Si la tarea carga datos, se completa desde "
                    + "su propia pantalla.", error);
        }
    }

    private Tarea tareaPendiente(String tareaId) {
        try {
            return flowable.tarea(tareaId);
        } catch (HttpClientErrorException.NotFound error) {
            throw new AccionNoPermitida("La tarea ya no está pendiente.");
        }
    }
}
