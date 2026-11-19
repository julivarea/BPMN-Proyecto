package ar.edu.unrc.gtpn.web.tareas;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
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

    /**
     * Tarea con la que termina la cancelación. Flowable no deja completarla mientras haya liberaciones sin terminar
     * (05-decisiones, limitación 2), así que la web no ofrece completarla hasta entonces.
     */
    static final String CONFIRMAR_COMPENSACIONES = "confirmarCompensaciones";

    /** Liberaciones que son tareas de usuario y hay que completar antes de confirmar las compensaciones. */
    static final Set<String> LIBERACIONES_DE_USUARIO = Set.of("liberarEquipamiento");

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

    /**
     * Por qué la tarea todavía no se puede completar, o {@code null} si se puede. Hoy solo aplica a *Confirmar
     * compensaciones*: queda bloqueada mientras haya liberaciones pendientes en la misma instancia. Si no se puede
     * consultar a Flowable no se bloquea: al completar, Flowable decide.
     */
    public String motivoDeBloqueo(Tarea tarea) {
        if (!CONFIRMAR_COMPENSACIONES.equals(tarea.clave())) {
            return null;
        }
        List<String> pendientes;
        try {
            pendientes = flowable.tareasDeLaInstancia(tarea.instanciaId()).stream()
                    .filter(otra -> LIBERACIONES_DE_USUARIO.contains(otra.clave()))
                    .map(Tarea::nombre)
                    .toList();
        } catch (RestClientException error) {
            return null;
        }
        if (pendientes.isEmpty()) {
            return null;
        }
        return "Primero hay que completar: " + String.join(", ", pendientes) + ".";
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
        String bloqueo = motivoDeBloqueo(tarea);
        if (bloqueo != null) {
            throw new AccionNoPermitida("Todavía no se puede completar esta tarea. " + bloqueo);
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
