package ar.edu.unrc.gtpn.proceso.estado;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registra cómo terminó la solicitud (enunciado 3.4). La actualización de la BD queda para el issue #25. */
public class RegistrarResultadoDelCursoListener implements ExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(RegistrarResultadoDelCursoListener.class);

    @Override
    public void notify(DelegateExecution execution) {
        log.info("Solicitud {} terminada con resultado {}",
                execution.getProcessInstanceBusinessKey(), execution.getVariable("resultado"));
    }
}
