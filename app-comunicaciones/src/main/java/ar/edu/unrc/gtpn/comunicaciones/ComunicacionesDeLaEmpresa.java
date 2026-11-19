package ar.edu.unrc.gtpn.comunicaciones;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
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

    /** Hora de inicio cuando se acuerda una fecha futura sin hora. */
    private static final LocalTime HORA_POR_DEFECTO = LocalTime.of(9, 0);
    /** Cuando la fecha sin hora es la de hoy, el curso empieza dentro de este tiempo: queda habilitado enseguida. */
    private static final long MINUTOS_PARA_HOY_SIN_HORA = 1;
    /** Fecha y hora con el desfase de la zona de la app, que Flowable no puede suponer (su servidor corre en UTC). */
    private static final DateTimeFormatter ISO_CON_DESFASE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter CON_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter SIN_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Formatos aceptados para la fecha acordada, en orden: el primero que entienda el texto, gana. */
    private static final List<Function<String, FechaAcordada>> FORMATOS = List.of(
            texto -> conHora(LocalDateTime.parse(texto)),
            texto -> new FechaAcordada(LocalDate.parse(texto), null),
            texto -> conHora(LocalDateTime.parse(texto, CON_HORA)),
            texto -> new FechaAcordada(LocalDate.parse(texto, SIN_HORA), null));

    /** Día acordado y su hora, o {@code null} si la empresa no la indicó. */
    private record FechaAcordada(LocalDate dia, LocalTime hora) {
    }

    private static FechaAcordada conHora(LocalDateTime fecha) {
        return new FechaAcordada(fecha.toLocalDate(), fecha.toLocalTime());
    }

    private final FlowableRest flowable;
    private final Clock reloj;

    ComunicacionesDeLaEmpresa(FlowableRest flowable) {
        this(flowable, Clock.systemDefaultZone());
    }

    /** Con el reloj explícito, para poder probar las fechas. */
    ComunicacionesDeLaEmpresa(FlowableRest flowable, Clock reloj) {
        this.flowable = flowable;
        this.reloj = reloj;
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
    private Map<String, Object> variables(Comunicacion comunicacion, String dato) {
        Map<String, Object> variables = new LinkedHashMap<>();
        comunicacion.variable().ifPresent(variable -> variables.put(variable, valor(comunicacion, dato)));
        return variables;
    }

    private Object valor(Comunicacion comunicacion, String dato) {
        if (comunicacion == Comunicacion.ACEPTACION) {
            return fechaISO(dato, reloj);
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
     * Convierte la fecha acordada al formato ISO-8601 con desfase que espera el timer del proceso
     * ({@code 2026-12-01T09:00:00-03:00}). Acepta {@code AAAA-MM-DD}, {@code DD/MM/AAAA} y las mismas con hora.
     *
     * <ul>
     *   <li>Sin hora y de un día futuro: empieza a las 09:00 de ese día.</li>
     *   <li>Sin hora y de hoy: empieza dentro de un minuto, así el curso queda habilitado apenas termina la preparación
     *       (para probar y para las demos, enunciado §3.5).</li>
     *   <li>Con hora: la que se indica. Hoy con una hora que ya pasó se acepta, y el curso queda habilitado enseguida.</li>
     *   <li>Una fecha anterior a hoy no se admite.</li>
     * </ul>
     *
     * <p>El desfase es el de la zona de la app: Flowable corre en UTC y sin él leería la hora local como UTC.
     */
    static String fechaISO(String texto, Clock reloj) {
        FechaAcordada acordada = interpretar(texto);
        LocalDate hoy = LocalDate.now(reloj);
        if (acordada.dia().isBefore(hoy)) {
            throw new IllegalArgumentException("La fecha de inicio no puede ser anterior a hoy.");
        }
        LocalDateTime inicio;
        if (acordada.hora() != null) {
            inicio = acordada.dia().atTime(acordada.hora());
        } else if (acordada.dia().equals(hoy)) {
            inicio = LocalDateTime.now(reloj).plusMinutes(MINUTOS_PARA_HOY_SIN_HORA);
        } else {
            inicio = acordada.dia().atTime(HORA_POR_DEFECTO);
        }
        return inicio.truncatedTo(ChronoUnit.SECONDS).atZone(reloj.getZone()).format(ISO_CON_DESFASE);
    }

    private static FechaAcordada interpretar(String texto) {
        String fecha = texto.trim();
        for (Function<String, FechaAcordada> formato : FORMATOS) {
            try {
                return formato.apply(fecha);
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
