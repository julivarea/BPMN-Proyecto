package ar.edu.unrc.gtpn.proceso.asignacion;

import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Asigna la tarea al administrativo con menos tareas pendientes (enunciado 3.4). La lógica queda para el issue #18. */
public class AsignarAdministrativoConMenosTareasListener implements TaskListener {

    private static final Logger log = LoggerFactory.getLogger(AsignarAdministrativoConMenosTareasListener.class);

    @Override
    public void notify(DelegateTask tarea) {
        log.info("Tarea {} creada; queda disponible para el grupo administracion", tarea.getName());
    }
}
