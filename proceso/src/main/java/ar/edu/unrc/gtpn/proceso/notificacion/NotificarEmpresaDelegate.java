package ar.edu.unrc.gtpn.proceso.notificacion;

import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simula el envío de una comunicación a la empresa solicitante; el enunciado no exige un envío real.
 * Cada tarea de notificación del modelo reutiliza esta clase configurando su propio campo "asunto".
 */
public class NotificarEmpresaDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(NotificarEmpresaDelegate.class);

    /** Inyectado por Flowable desde {@code <flowable:field name="asunto">}. */
    private Expression asunto;

    @Override
    public void execute(DelegateExecution execution) {
        log.info("Notificación a la empresa (solicitud {}): {}",
                execution.getProcessInstanceBusinessKey(), asunto.getValue(execution));
    }
}
