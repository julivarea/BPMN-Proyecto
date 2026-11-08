package ar.edu.unrc.gtpn.proceso.recursos;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Libera el aula virtual (compensación, enunciado 1.3). La simulación queda para el issue #19. */
public class LiberarAulaVirtualDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(LiberarAulaVirtualDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        log.info("Aula virtual liberada para la solicitud {}", execution.getProcessInstanceBusinessKey());
    }
}
