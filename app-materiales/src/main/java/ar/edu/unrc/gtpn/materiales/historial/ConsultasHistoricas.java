package ar.edu.unrc.gtpn.materiales.historial;

import java.time.Duration;
import java.util.Map;

import org.flowable.engine.HistoryService;

/** Consultas sobre el historial de ejecuciones del proceso (enunciado 4.3). */
public final class ConsultasHistoricas {

    private final HistoryService historial;

    public ConsultasHistoricas(HistoryService historial) {
        this.historial = historial;
    }

    /** Cantidad de instancias terminadas por resultado; los porcentajes se calculan sobre su total (4.3.1). */
    public Map<String, Long> resultadosDeLasSolicitudes() {
        // TODO: Implementación pendiente
        throw new UnsupportedOperationException("Implementación pendiente");
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
}
