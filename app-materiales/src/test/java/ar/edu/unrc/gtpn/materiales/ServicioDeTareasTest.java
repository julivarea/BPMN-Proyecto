package ar.edu.unrc.gtpn.materiales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.test.Deployment;
import org.flowable.engine.test.FlowableTest;
import org.flowable.idm.api.User;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@FlowableTest
class ServicioDeTareasTest {

    private ServicioDeTareas servicio;
    private TaskService tareas;
    private RuntimeService runtime;

    @BeforeEach
    void preparar(ProcessEngine motor) {
        servicio = new ServicioDeTareas(motor);
        tareas = motor.getTaskService();
        runtime = motor.getRuntimeService();
        if (motor.getIdentityService().createGroupQuery().groupId("administracion").singleResult() == null) {
            motor.getIdentityService().saveGroup(motor.getIdentityService().newGroup("administracion"));
        }
        if (motor.getIdentityService().createUserQuery().userId("ana").singleResult() == null) {
            User ana = motor.getIdentityService().newUser("ana");
            motor.getIdentityService().saveUser(ana);
        }
        if (motor.getIdentityService().createUserQuery().userId("ana").memberOfGroup("administracion")
                .singleResult() == null) {
            motor.getIdentityService().createMembership("ana", "administracion");
        }
    }

    @Test
    @Deployment(resources = "procesos/tarea-materiales.bpmn20.xml")
    void reclamaUnaTareaDisponibleParaSuGrupo() {
        Task tarea = iniciarTarea();

        servicio.reclamar(tarea.getId(), "ana");

        assertEquals("ana", tareas.createTaskQuery().taskId(tarea.getId()).singleResult().getAssignee());
    }

    @Test
    @Deployment(resources = "procesos/tarea-materiales.bpmn20.xml")
    void noPuedeReclamarUnaTareaYaAsignada() {
        Task tarea = iniciarTarea();
        tareas.claim(tarea.getId(), "otro");

        ServicioDeTareas.TareaNoDisponible error = assertThrows(ServicioDeTareas.TareaNoDisponible.class,
                () -> servicio.reclamar(tarea.getId(), "ana"));

        assertTrue(error.getMessage().contains("ya fue reclamada"));
    }

    @Test
    @Deployment(resources = "procesos/tarea-materiales.bpmn20.xml")
    void completaUnaTareaAsignadaAlUsuario() {
        Task tarea = iniciarTarea();
        tareas.claim(tarea.getId(), "ana");

        servicio.completar(tarea.getId(), "ana");

        assertEquals(0, tareas.createTaskQuery().taskId(tarea.getId()).count());
    }

    @Test
    @Deployment(resources = "procesos/tarea-materiales.bpmn20.xml")
    void noCompletaUnaTareaAsignadaAOtroUsuario() {
        Task tarea = iniciarTarea();
        tareas.claim(tarea.getId(), "otro");

        assertThrows(ServicioDeTareas.OperacionNoPermitida.class,
                () -> servicio.completar(tarea.getId(), "ana"));
        assertNotNull(tareas.createTaskQuery().taskId(tarea.getId()).singleResult());
    }

    @Test
    @Deployment(resources = "procesos/tarea-materiales.bpmn20.xml")
    void noCompletaUnaTareaSinAsignar() {
        Task tarea = iniciarTarea();

        assertThrows(ServicioDeTareas.OperacionNoPermitida.class,
                () -> servicio.completar(tarea.getId(), "ana"));
        assertNotNull(tareas.createTaskQuery().taskId(tarea.getId()).singleResult());
    }

    private Task iniciarTarea() {
        return tareas.createTaskQuery().processInstanceId(
                runtime.startProcessInstanceByKey("prepararMaterialesTest", "curso-1").getId()).singleResult();
    }
}
