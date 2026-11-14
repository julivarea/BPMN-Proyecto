package ar.edu.unrc.gtpn.proceso.recursos;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Libera el aula virtual (compensación, enunciado 1.3 y 4.1.3) contra la plataforma simulada. */
public class LiberarAulaVirtualDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(LiberarAulaVirtualDelegate.class);

    private final ServicioAulasVirtuales virtuales;

    /** Flowable instancia los delegates con el constructor sin argumentos. */
    public LiberarAulaVirtualDelegate() {
        this(new ServicioAulasVirtualesSimulado());
    }

    /** Para los tests: plataforma simulada o doble. */
    public LiberarAulaVirtualDelegate(ServicioAulasVirtuales virtuales) {
        this.virtuales = virtuales;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String solicitud = execution.getProcessInstanceBusinessKey();
        virtuales.liberar(solicitud);
        log.info("Aula virtual liberada para la solicitud {}", solicitud);
    }
}
