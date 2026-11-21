package ar.edu.unrc.gtpn.proceso.asignacion;

import java.util.Comparator;
import java.util.List;

import org.flowable.engine.IdentityService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.TaskHelper;
import org.flowable.idm.api.User;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asigna la tarea al administrativo con menos tareas pendientes asignadas (enunciado 3.4).
 * En caso de empate, aplica como criterio fijo el orden alfabético del ID del usuario.
 */
public class AsignarAdministrativoConMenosTareasListener implements TaskListener {

    private static final Logger log = LoggerFactory.getLogger(AsignarAdministrativoConMenosTareasListener.class);
    private static final String GRUPO_ADMINISTRACION = "administracion";

    @Override
    public void notify(DelegateTask tarea) {
        // Obtenemos los servicios desde el Contexto global de Flowable
        TaskService taskService = CommandContextUtil.getProcessEngineConfiguration().getTaskService();
        IdentityService identityService = CommandContextUtil.getProcessEngineConfiguration().getIdentityService();

        // 1. Obtener los miembros del grupo "administracion"
        List<User> administrativos = identityService.createUserQuery()
                .memberOfGroup(GRUPO_ADMINISTRACION)
                .list();

        if (administrativos == null || administrativos.isEmpty()) {
            log.warn("No hay usuarios registrados en el grupo {}", GRUPO_ADMINISTRACION);
            return;
        }

        // 2. Buscar el usuario con menor cantidad de tareas pendientes asignadas.
        // Criterio de desempate documentado: orden alfabético natural del ID de usuario (user.getId()).
        User seleccionado = administrativos.stream()
                .min(Comparator
                        .comparingLong((User user) -> contarTareasPendientes(taskService, user.getId()))
                        .thenComparing(User::getId)
                )
                .orElseThrow();

        // 3. Asignar la tarea al administrativo seleccionado
        // Con tarea.setAssignee la tarea queda asignada pero el historial no se entera (su registro ya se creó sin
        // asignado) y la consulta de tareas completadas por persona (§4.3.3) la dejaría afuera: se asigna por el motor.
        TaskHelper.changeTaskAssignee((TaskEntity) tarea, seleccionado.getId());
    }

    /**
     * Cuenta únicamente las tareas activas pendientes asignadas directamente al usuario.
     * No computa las tareas que sólo tienen asignado al grupo como candidato.
     */
    private long contarTareasPendientes(TaskService taskService, String userId) {
        return taskService.createTaskQuery()
                .taskAssignee(userId)
                .active()
                .count();
    }
}
