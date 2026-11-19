package ar.edu.unrc.gtpn.proceso.compensacion;

import java.util.List;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.engine.impl.context.Context;
import org.flowable.engine.runtime.Execution;
import org.flowable.task.service.delegate.DelegateTask;

/**
 * Task listener {@code complete} de *Liberar equipamiento*. Avisa que el equipamiento ya se liberó con la señal
 * {@code equipamientoLiberado}, que espera la cancelación antes de notificar el cierre a la empresa (enunciado 1.3:
 * el cierre se notifica una vez concluidas las compensaciones).
 *
 * <p>El flujo que lanza la compensación no espera a los manejadores que son tareas de usuario (docs/05, limitación 2),
 * así que la espera se modela con esta señal. Se entrega solo a la ejecución de <b>esta</b> instancia que la espera: una señal
 * sin destinatario llegaría a todas las instancias que estén esperando. Si nadie la espera, por ejemplo en el cierre
 * normal del curso, no hace nada.
 */
public class EquipamientoLiberadoListener implements TaskListener {

    static final String SENAL = "equipamientoLiberado";

    @Override
    public void notify(DelegateTask tarea) {
        RuntimeService runtime = Context.getProcessEngineConfiguration().getRuntimeService();
        List<Execution> esperando = runtime.createExecutionQuery()
                .processInstanceId(tarea.getProcessInstanceId())
                .signalEventSubscriptionName(SENAL)
                .list();
        for (Execution ejecucion : esperando) {
            runtime.signalEventReceived(SENAL, ejecucion.getId());
        }
    }
}
