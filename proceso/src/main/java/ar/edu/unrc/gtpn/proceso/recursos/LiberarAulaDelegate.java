package ar.edu.unrc.gtpn.proceso.recursos;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Libera el aula física reservada (compensación, enunciado 1.3 y 4.1.3): lee {@code aulaAsignada} y se la devuelve
 * al web service de aulas. Si la instancia no llegó a reservar, no hay nada que liberar.
 */
public class LiberarAulaDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(LiberarAulaDelegate.class);

    private final ServicioAulas aulas;

    /** Flowable instancia los delegates con el constructor sin argumentos. */
    public LiberarAulaDelegate() {
        this(new ServicioAulasSimulado());
    }

    /** Para los tests: web service simulado o doble. */
    public LiberarAulaDelegate(ServicioAulas aulas) {
        this.aulas = aulas;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String solicitud = execution.getProcessInstanceBusinessKey();
        Object aula = execution.getVariable("aulaAsignada");
        if (aula == null) {
            log.info("La solicitud {} no tiene aula física reservada: no hay nada que liberar", solicitud);
            return;
        }
        aulas.liberar(solicitud, aula.toString());
        log.info("Aula {} liberada para la solicitud {}", aula, solicitud);
    }
}
