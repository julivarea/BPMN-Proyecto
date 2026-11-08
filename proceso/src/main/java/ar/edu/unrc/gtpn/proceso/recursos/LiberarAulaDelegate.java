package ar.edu.unrc.gtpn.proceso.recursos;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Libera el aula física reservada (compensación, enunciado 1.3). La simulación del web service queda para el issue #19. */
public class LiberarAulaDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(LiberarAulaDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        log.info("Aula física liberada para la solicitud {}", execution.getProcessInstanceBusinessKey());
    }
}
