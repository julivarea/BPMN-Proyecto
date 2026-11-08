package ar.edu.unrc.gtpn.web.flowable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tarea de usuario pendiente en Flowable.
 *
 * @param clave      ID de la tarea en el modelo BPMN (por ejemplo {@code registrarYElaborarPropuesta})
 * @param instanciaId instancia del proceso a la que pertenece
 * @param asignado   usuario asignado, o {@code null} si todavía nadie la reclamó
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Tarea(
        @JsonProperty("id") String id,
        @JsonProperty("name") String nombre,
        @JsonProperty("taskDefinitionKey") String clave,
        @JsonProperty("processInstanceId") String instanciaId,
        @JsonProperty("assignee") String asignado) {
}
