package ar.edu.unrc.gtpn.web.flowable;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tarea de usuario pendiente en Flowable.
 *
 * @param clave      ID de la tarea en el modelo BPMN (por ejemplo {@code registrarYElaborarPropuesta})
 * @param instanciaId instancia del proceso a la que pertenece
 * @param asignado   usuario asignado, o {@code null} si todavía nadie la reclamó
 * @param creada     cuándo la creó el proceso
 * @param solicitud  businessKey de la instancia (el ID del curso), o {@code null} si no se consultó
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Tarea(
        @JsonProperty("id") String id,
        @JsonProperty("name") String nombre,
        @JsonProperty("taskDefinitionKey") String clave,
        @JsonProperty("processInstanceId") String instanciaId,
        @JsonProperty("assignee") String asignado,
        @JsonProperty("createTime") OffsetDateTime creada,
        String solicitud) {

    /** La misma tarea, con la solicitud a la que pertenece. */
    public Tarea conSolicitud(String solicitud) {
        return new Tarea(id, nombre, clave, instanciaId, asignado, creada, solicitud);
    }

    /** Fecha de creación en la hora local, para mostrarla en las páginas. */
    public LocalDateTime creadaEnHoraLocal() {
        return creada.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
