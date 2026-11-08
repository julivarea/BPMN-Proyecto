package ar.edu.unrc.gtpn.proceso.recursos;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reserva el aula física (enunciado 4.1.3). La simulación del web service queda para el issue #19. */
public class ReservarAulaDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ReservarAulaDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        log.info("Aula reservada para la solicitud {}", execution.getProcessInstanceBusinessKey());
    }
}
