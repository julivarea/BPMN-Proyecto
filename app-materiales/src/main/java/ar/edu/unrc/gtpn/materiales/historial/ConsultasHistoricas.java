package ar.edu.unrc.gtpn.materiales.historial;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.flowable.engine.HistoryService;
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
    public Map<String, Duration> duracionPromedioDeLasRamas() {
        // TODO: Implementación pendiente
        throw new UnsupportedOperationException("Implementación pendiente");
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
}
