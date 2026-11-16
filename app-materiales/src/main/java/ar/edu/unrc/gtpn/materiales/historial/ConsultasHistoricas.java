package ar.edu.unrc.gtpn.materiales.historial;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;

/**
 * Consultas sobre el historial de ejecuciones del proceso (enunciado 4.3).
 *
 * <p>Las instancias terminadas guardan en la variable {@code resultado} cómo terminó el curso: los valores finales son
 * {@code FINALIZADO}, {@code CANCELADO}, {@code RECHAZADO}, {@code SIN_RESPUESTA} y {@code NO_VIABLE} (docs/07 §2).
 */
public final class ConsultasHistoricas {

    /** Proceso cuyas ejecuciones se consultan (docs/06). */
    static final String PROCESO = "organizarCurso";

    /** Resultado que se usa cuando una instancia terminó sin la variable (por ejemplo, datos viejos). */
    static final String SIN_RESULTADO = "SIN_RESULTADO";

    /** Compuerta donde confluyen las tres ramas: si se completó, la preparación no fue cancelada. */
    static final String PREPARACION_COMPLETA = "preparacionCompleta";

    /** Primera actividad de cada rama de la preparación: marca el inicio de la rama. */
    static final String INICIO_INSCRIPCIONES = "nominaRecibida";
    static final String INICIO_MATERIALES = "prepararMateriales";
    static final String INICIO_RECURSOS = "evaluarRecursos";

    /** Actividades que cierran cada rama (en recursos, la que se haya seleccionado). */
    static final String FIN_INSCRIPCIONES = "confeccionarListaDefinitiva";
    static final String FIN_MATERIALES = "prepararMateriales";
    static final List<String> FIN_RECURSOS = List.of("reservarAula", "solicitarAulaVirtual", "contratarEquipamiento");

    private final HistoryService historial;

    public ConsultasHistoricas(HistoryService historial) {
        this.historial = historial;
    }

    /**
     * Cantidad y porcentaje de instancias terminadas por resultado (4.3.1). El porcentaje se calcula sobre el total de
     * instancias terminadas. La lista va de mayor a menor cantidad y, a igual cantidad, por nombre del resultado.
     */
    public List<ResultadoSolicitud> resultadosDeLasSolicitudes() {
        List<HistoricProcessInstance> terminadas = historial.createHistoricProcessInstanceQuery()
                .processDefinitionKey(PROCESO)
                .finished()
                .includeProcessVariables()
                .list();
        long total = terminadas.size();

        Map<String, Long> cantidades = terminadas.stream()
                .map(ConsultasHistoricas::resultadoDe)
                .collect(Collectors.groupingBy(resultado -> resultado, Collectors.counting()));

        return cantidades.entrySet().stream()
                .map(cantidad -> new ResultadoSolicitud(
                        cantidad.getKey(),
                        cantidad.getValue(),
                        total == 0 ? 0.0 : cantidad.getValue() * 100.0 / total))
                .sorted(Comparator.comparingLong(ResultadoSolicitud::cantidad).reversed()
                        .thenComparing(ResultadoSolicitud::resultado))
                .toList();
    }

    /** Duración promedio de las ramas de inscripciones, materiales y recursos, sin preparaciones canceladas (4.3.2). */
    public DuracionRamas duracionPromedioDeLasRamas() {
        Set<String> instancias = historial.createHistoricActivityInstanceQuery()
                .activityId(PREPARACION_COMPLETA)
                .finished()
                .list()
                .stream()
                .map(HistoricActivityInstance::getProcessInstanceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ActividadHistorica> actividades = new ArrayList<>();
        for (String instancia : instancias) {
            historial.createHistoricActivityInstanceQuery()
                    .processInstanceId(instancia)
                    .list()
                    .forEach(actividad -> actividades.add(new ActividadHistorica(
                            actividad.getProcessInstanceId(),
                            actividad.getActivityId(),
                            actividad.getStartTime() == null ? null : actividad.getStartTime().toInstant(),
                            actividad.getEndTime() == null ? null : actividad.getEndTime().toInstant())));
        }
        return promediar(actividades);
    }

    /**
     * Calcula el promedio de las tres ramas sobre las instancias que tienen las tres completas. Una instancia sin
     * {@code preparacionCompleta} (por ejemplo, cancelada durante la preparación) no aporta actividades porque no se
     * consultó; si a una le falta el fin de alguna rama, se descarta para no falsear el promedio.
     */
    static DuracionRamas promediar(List<ActividadHistorica> actividades) {
        Map<String, List<ActividadHistorica>> porInstancia = actividades.stream()
                .collect(Collectors.groupingBy(ActividadHistorica::instancia, LinkedHashMap::new, Collectors.toList()));

        Duration inscripciones = Duration.ZERO;
        Duration materiales = Duration.ZERO;
        Duration recursos = Duration.ZERO;
        long total = 0;
        for (List<ActividadHistorica> instancia : porInstancia.values()) {
            Duration duracionInscripciones = duracion(instancia, INICIO_INSCRIPCIONES, List.of(FIN_INSCRIPCIONES));
            Duration duracionMateriales = duracion(instancia, INICIO_MATERIALES, List.of(FIN_MATERIALES));
            Duration duracionRecursos = duracion(instancia, INICIO_RECURSOS, FIN_RECURSOS);
            if (duracionInscripciones == null || duracionMateriales == null || duracionRecursos == null) {
                continue;
            }
            inscripciones = inscripciones.plus(duracionInscripciones);
            materiales = materiales.plus(duracionMateriales);
            recursos = recursos.plus(duracionRecursos);
            total++;
        }
        if (total == 0) {
            return new DuracionRamas(Duration.ZERO, Duration.ZERO, Duration.ZERO, 0);
        }
        return new DuracionRamas(
                inscripciones.dividedBy(total),
                materiales.dividedBy(total),
                recursos.dividedBy(total),
                total);
    }

    private static Duration duracion(List<ActividadHistorica> actividades, String inicioId, List<String> finIds) {
        Instant inicio = actividades.stream()
                .filter(actividad -> inicioId.equals(actividad.actividad()) && actividad.inicio() != null)
                .map(ActividadHistorica::inicio)
                .min(Comparator.naturalOrder())
                .orElse(null);
        Instant fin = actividades.stream()
                .filter(actividad -> finIds.contains(actividad.actividad()) && actividad.fin() != null)
                .map(ActividadHistorica::fin)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (inicio == null || fin == null || fin.isBefore(inicio)) {
            return null;
        }
        return Duration.between(inicio, fin);
    }

    /** Tareas de usuario completadas por cada administrativo y docente, sin pendientes ni canceladas (4.3.3). */
    public Map<String, Long> tareasCompletadasPorPersona() {
        // TODO: Implementación pendiente
        throw new UnsupportedOperationException("Implementación pendiente");
    }

    private static String resultadoDe(HistoricProcessInstance instancia) {
        Object resultado = instancia.getProcessVariables() == null
                ? null
                : instancia.getProcessVariables().get("resultado");
        return resultado == null || resultado.toString().isBlank() ? SIN_RESULTADO : resultado.toString();
    }

    /** Fila de la tabla del §4.3.1: un resultado, cuántas solicitudes terminaron así y qué porcentaje representan. */
    public record ResultadoSolicitud(String resultado, long cantidad, double porcentaje) {
    }

    /** Promedios del §4.3.2 y cuántas instancias completaron la preparación (base del promedio). */
    public record DuracionRamas(Duration inscripciones, Duration materiales, Duration recursos, long totalInstancias) {
    }

    /** Una actividad del historial reducida a lo que necesita el cálculo: instancia, ID, inicio y fin. */
    public record ActividadHistorica(String instancia, String actividad, Instant inicio, Instant fin) {
    }
}
