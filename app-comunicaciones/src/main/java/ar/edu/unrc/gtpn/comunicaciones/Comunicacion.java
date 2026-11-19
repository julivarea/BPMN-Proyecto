package ar.edu.unrc.gtpn.comunicaciones;

import java.util.Optional;

/**
 * Comunicaciones que la empresa puede enviar durante la negociación de la propuesta (enunciado 4.2.2, issue #16).
 *
 * <p>Cada comunicación corresponde a un mensaje del modelo BPMN y, si lleva datos, a una variable del proceso:
 * la aceptación fija {@code fechaInicioCurso} (la fecha acordada), la nómina lleva
 * {@code participantes} y la solicitud de modificaciones lleva {@code observacionesEmpresa}. El rechazo y la
 * cancelación no llevan datos. Los nombres son los de
 * docs/06-guia-implementacion-modelo.md §3.
 */
enum Comunicacion {

    ACEPTACION("aceptacionPropuesta", "Aceptar propuesta", "fechaInicioCurso",
            "Fecha de inicio acordada: AAAA-MM-DD o DD/MM/AAAA, con hora opcional (HH:mm). Sin hora, hoy empieza en 1 minuto"),
    RECHAZO("rechazoPropuesta", "Rechazar propuesta", null, null),
    MODIFICACIONES("modificacionesPropuesta", "Pedir modificaciones", "observacionesEmpresa",
            "Observaciones para la institución"),
    NOMINA_PARTICIPANTES("nominaParticipantes", "Enviar nómina de participantes", "participantes",
            "Participantes (uno por línea o separados por coma)"),
    CANCELACION_CURSO("cancelacionCurso", "Cancelar curso", null, null);

    private final String mensaje;
    private final String etiqueta;
    private final String variable;
    private final String pedido;

    Comunicacion(String mensaje, String etiqueta, String variable, String pedido) {
        this.mensaje = mensaje;
        this.etiqueta = etiqueta;
        this.variable = variable;
        this.pedido = pedido;
    }

    /** Nombre del mensaje en el modelo BPMN, tal como lo espera Flowable. */
    String mensaje() {
        return mensaje;
    }

    /** Texto del botón en la ventana. */
    String etiqueta() {
        return etiqueta;
    }

    /** Variable del proceso que la comunicación lleva, o vacío si no lleva datos. */
    Optional<String> variable() {
        return Optional.ofNullable(variable);
    }

    /** Qué se le pide al usuario antes de enviarla, o vacío si no lleva datos. */
    Optional<String> pedido() {
        return Optional.ofNullable(pedido);
    }
}
