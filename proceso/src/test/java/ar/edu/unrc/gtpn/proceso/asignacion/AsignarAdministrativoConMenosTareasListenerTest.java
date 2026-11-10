package ar.edu.unrc.gtpn.proceso.asignacion;

import org.flowable.engine.IdentityService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.TaskService;
import org.flowable.engine.test.FlowableTest;
import org.flowable.task.api.Task;
import org.flowable.task.service.delegate.DelegateTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@FlowableTest
public class AsignarAdministrativoConMenosTareasListenerTest {

    private final AsignarAdministrativoConMenosTareasListener listener = new AsignarAdministrativoConMenosTareasListener();

    @BeforeEach
    void setUp(IdentityService identityService, TaskService taskService) {
        // Limpieza de tareas pendientes previas
        taskService.createTaskQuery().list().forEach(t -> taskService.deleteTask(t.getId(), true));

        // Asegurar la existencia del grupo administracion
        if (identityService.createGroupQuery().groupId("administracion").count() == 0) {
            var grupo = identityService.newGroup("administracion");
            grupo.setName("Administración");
            identityService.saveGroup(grupo);
        }

        crearUsuarioSiNoExiste(identityService, "admin_a");
        crearUsuarioSiNoExiste(identityService, "admin_b");
        crearUsuarioSiNoExiste(identityService, "jefe_admin");
    }

    private void crearUsuarioSiNoExiste(IdentityService identityService, String userId) {
        if (identityService.createUserQuery().userId(userId).count() == 0) {
            var user = identityService.newUser(userId);
            identityService.saveUser(user);
            identityService.createMembership(userId, "administracion");
        }
    }

    @Test
    void testAsignacionAUsuarioMenosCargado(TaskService taskService, ManagementService managementService) {
        // Carga de tareas: jefe_admin tiene 2, admin_a tiene 1, admin_b tiene 0
        Task t1 = taskService.newTask(); t1.setAssignee("jefe_admin"); taskService.saveTask(t1);
        Task t2 = taskService.newTask(); t2.setAssignee("jefe_admin"); taskService.saveTask(t2);
        Task t3 = taskService.newTask(); t3.setAssignee("admin_a"); taskService.saveTask(t3);

        Task nuevaTarea = taskService.newTask();

        // Ejecución del listener dentro del contexto del motor
        managementService.executeCommand(commandContext -> {
            listener.notify((DelegateTask) nuevaTarea);
            return null;
        });

        // La nueva tarea debe asignarse a admin_b por ser el de menor carga
        assertEquals("admin_b", nuevaTarea.getAssignee());
    }

    @Test
    void testDesempatePorOrdenAlfabetico(TaskService taskService, ManagementService managementService) {
        // Carga de tareas: jefe_admin tiene 2, admin_a y admin_b tienen 0 tareas (empate)
        Task t1 = taskService.newTask(); t1.setAssignee("jefe_admin"); taskService.saveTask(t1);
        Task t2 = taskService.newTask(); t2.setAssignee("jefe_admin"); taskService.saveTask(t2);

        Task nuevaTarea = taskService.newTask();

        managementService.executeCommand(commandContext -> {
            listener.notify((DelegateTask) nuevaTarea);
            return null;
        });

        // Ante el empate en 0 tareas, se aplica el criterio alfabético ("admin_a" < "admin_b")
        assertEquals("admin_a", nuevaTarea.getAssignee());
    }
}