package ar.edu.unrc.gtpn.materiales;

import java.util.List;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.FlowableTaskAlreadyClaimedException;
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

    /** Reclama una tarea disponible para el usuario. */
    void reclamar(String tareaId, String usuario) {
        try {
            motor.getTaskService().claim(tareaId, usuario);
        } catch (FlowableTaskAlreadyClaimedException error) {
            throw new TareaNoDisponible("La tarea ya fue reclamada por otro usuario.", error);
        }
    }

    /**
     * Completa una tarea solo si está asignada al usuario autenticado.
     *
     * <p>Flowable no impone esta restricción al completar desde el motor, por eso se valida antes de llamar a
     * {@code complete}.
     */
    void completar(String tareaId, String usuario) {
        Task tarea = motor.getTaskService().createTaskQuery().taskId(tareaId).singleResult();
        if (tarea == null || !usuario.equals(tarea.getAssignee())) {
            throw new OperacionNoPermitida("Solo el usuario asignado puede completar la tarea.");
        }
        motor.getTaskService().complete(tareaId);
    }

    /** Solicitud (businessKey) a la que pertenece la tarea, para mostrarla en la lista. */
    String solicitudDe(Task tarea) {
        return motor.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(tarea.getProcessInstanceId())
                .singleResult()
                .getBusinessKey();
    }

    static final class TareaNoDisponible extends IllegalStateException {
        TareaNoDisponible(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    static final class OperacionNoPermitida extends IllegalStateException {
        OperacionNoPermitida(String mensaje) {
            super(mensaje);
        }
    }
}
