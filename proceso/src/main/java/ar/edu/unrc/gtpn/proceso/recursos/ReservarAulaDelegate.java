package ar.edu.unrc.gtpn.proceso.recursos;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reserva el aula física del curso (enunciado 4.1.3, I-10) y guarda el aula en la variable {@code aulaAsignada},
 * que después usa *Liberar aula* para compensar.
 */
public class ReservarAulaDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ReservarAulaDelegate.class);

    private final ServicioAulas aulas;

    /** Flowable instancia los delegates con el constructor sin argumentos. */
    public ReservarAulaDelegate() {
        this(new ServicioAulasSimulado());
    }

    /** Para los tests: web service simulado o doble. */
    public ReservarAulaDelegate(ServicioAulas aulas) {
        this.aulas = aulas;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String solicitud = execution.getProcessInstanceBusinessKey();
        String aula = aulas.reservar(solicitud);
        execution.setVariable("aulaAsignada", aula);
        log.info("Aula {} asignada a la solicitud {}", aula, solicitud);
    }
}
