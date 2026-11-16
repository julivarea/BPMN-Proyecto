package ar.edu.unrc.gtpn.web.flowable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Actividad de una instancia del proceso, tal como la informa el historial de Flowable.
 *
 * @param id     ID de la actividad en el modelo BPMN (por ejemplo {@code evaluarRecursos})
 * @param nombre nombre de la actividad en el modelo, que es el que se le muestra a la empresa
 * @param tipo   tipo BPMN ({@code userTask}, {@code serviceTask}, {@code intermediateCatchEvent}, …)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Actividad(
        @JsonProperty("activityId") String id,
        @JsonProperty("activityName") String nombre,
        @JsonProperty("activityType") String tipo) {
}
