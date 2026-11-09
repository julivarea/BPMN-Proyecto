package ar.edu.unrc.gtpn.materiales;

import java.util.List;

import org.flowable.engine.ProcessEngine;
import org.flowable.task.api.Task;

/** Lo que la app hace con el motor: identificar al usuario y consultar sus tareas "Preparar materiales". */
final class ServicioDeTareas {

    /** ID de la tarea en el modelo BPMN (docs/06-guia-implementacion-modelo.md). */
    static final String PREPARAR_MATERIALES = "prepararMateriales";

    private final ProcessEngine motor;

    ServicioDeTareas(ProcessEngine motor) {
        this.motor = motor;
    }

    /** Valida usuario y contraseña contra los usuarios de Flowable. */
    boolean credencialesValidas(String usuario, String clave) {
        return motor.getIdentityService().checkPassword(usuario, clave);
    }

    /** Tareas "Preparar materiales" asignadas al usuario o disponibles para sus grupos, de la más vieja a la más nueva. */
    List<Task> tareasDe(String usuario) {
        return motor.getTaskService().createTaskQuery()
                .taskDefinitionKey(PREPARAR_MATERIALES)
                .taskCandidateOrAssigned(usuario)
                .orderByTaskCreateTime().asc()
                .list();
    }

    /** Solicitud (businessKey) a la que pertenece la tarea, para mostrarla en la lista. */
    String solicitudDe(Task tarea) {
        return motor.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(tarea.getProcessInstanceId())
                .singleResult()
                .getBusinessKey();
    }
}
