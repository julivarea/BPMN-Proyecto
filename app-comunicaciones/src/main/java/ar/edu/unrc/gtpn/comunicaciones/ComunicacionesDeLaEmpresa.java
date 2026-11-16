package ar.edu.unrc.gtpn.comunicaciones;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Comunicaciones que la empresa puede enviar sobre una solicitud (enunciado 4.2.2, X-03 y X-04), tanto durante la
 * negociación como durante la preparación: cuáles están habilitadas según lo que la instancia está esperando y el
 * envío del mensaje a Flowable.
 */
final class ComunicacionesDeLaEmpresa {

    /** Hora que se usa cuando la empresa acuerda una fecha sin hora. */
    private static final LocalTime HORA_POR_DEFECTO = LocalTime.of(9, 0);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter CON_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter SIN_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Formatos aceptados para la fecha acordada, en orden: el primero que entienda el texto, gana. */
    private static final List<Function<String, LocalDateTime>> FORMATOS = List.of(
            LocalDateTime::parse,
            fecha -> LocalDate.parse(fecha).atTime(HORA_POR_DEFECTO),
            fecha -> LocalDateTime.parse(fecha, CON_HORA),
            fecha -> LocalDate.parse(fecha, SIN_HORA).atTime(HORA_POR_DEFECTO));

    private final FlowableRest flowable;

    ComunicacionesDeLaEmpresa(FlowableRest flowable) {
        this.flowable = flowable;
    }

    /**
     * Comunicaciones que la instancia está esperando en este momento. Si no espera ninguna (por ejemplo, mientras
     * la institución elabora la propuesta), el conjunto queda vacío y todos los botones se deshabilitan.
     */
    Set<Comunicacion> habilitadas(Solicitud solicitud) {
        Set<Comunicacion> habilitadas = EnumSet.noneOf(Comunicacion.class);
        for (Comunicacion comunicacion : Comunicacion.values()) {
            if (flowable.ejecucionEsperando(solicitud.instanciaId(), comunicacion.mensaje()).isPresent()) {
                habilitadas.add(comunicacion);
            }
        }
        return habilitadas;
    }

    /**
     * Envía la comunicación y destraba la instancia.
     *
     * @param dato lo que pidió la comunicación ({@link Comunicacion#pedido()}), vacío si no lleva datos
     * @throws ComunicacionNoHabilitada si la instancia dejó de esperar ese mensaje
     * @throws IllegalArgumentException si la fecha acordada no se entiende
     */
    void enviar(Solicitud solicitud, Comunicacion comunicacion, String dato) {
        String ejecucion = flowable.ejecucionEsperando(solicitud.instanciaId(), comunicacion.mensaje())
                .orElseThrow(() -> new ComunicacionNoHabilitada(comunicacion));
        flowable.enviarMensaje(ejecucion, comunicacion.mensaje(), variables(comunicacion, dato));
    }

    /** Variables del proceso que acompañan al mensaje. */
    private static Map<String, Object> variables(Comunicacion comunicacion, String dato) {
        Map<String, Object> variables = new LinkedHashMap<>();
        comunicacion.variable().ifPresent(variable -> variables.put(variable, valor(comunicacion, dato)));
        return variables;
    }

    private static Object valor(Comunicacion comunicacion, String dato) {
        if (comunicacion == Comunicacion.ACEPTACION) {
            return fechaISO(dato);
        }
        if (comunicacion == Comunicacion.NOMINA_PARTICIPANTES) {
            return participantes(dato);
        }
        return dato.trim();
    }

    /** Convierte la entrada de la app en la lista JSON que Flowable guarda en {@code participantes}. */
    private static List<String> participantes(String dato) {
        List<String> resultado = List.of(dato.split("[,\\r\\n]")).stream()
                .map(String::trim)
                .filter(participante -> !participante.isEmpty())
                .toList();
        if (resultado.isEmpty()) {
            throw new IllegalArgumentException("La nómina debe incluir al menos un participante.");
        }
        return resultado;
    }

    /**
     * Convierte la fecha acordada al formato ISO-8601 que espera el timer del proceso
     * ({@code 2026-12-01T09:00:00}). Acepta {@code AAAA-MM-DD}, {@code DD/MM/AAAA} y las mismas con hora.
     */
    static String fechaISO(String texto) {
        String fecha = texto.trim();
        for (Function<String, LocalDateTime> formato : FORMATOS) {
            try {
                return formato.apply(fecha).format(ISO);
            } catch (DateTimeParseException noEra) {
                // se prueba el formato siguiente
            }
        }
        throw new IllegalArgumentException(
                "No se entiende la fecha \"" + texto + "\": usá AAAA-MM-DD o DD/MM/AAAA.");
    }

    /** La instancia no está esperando el mensaje de esa comunicación: no se puede enviar. */
    static final class ComunicacionNoHabilitada extends RuntimeException {
        ComunicacionNoHabilitada(Comunicacion comunicacion) {
            super("La solicitud no está esperando \"" + comunicacion.etiqueta() + "\" en este momento.");
        }
    }
}
