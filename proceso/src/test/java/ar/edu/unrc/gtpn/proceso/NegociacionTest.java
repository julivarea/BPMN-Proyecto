package ar.edu.unrc.gtpn.proceso;

import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.test.Deployment;
import org.flowable.engine.test.FlowableTest;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@FlowableTest
public class NegociacionTest {

    @BeforeEach
    void setUp(RuntimeService runtimeService) {
        // Limpiar ejecuciones previas de la BD en memoria
        runtimeService.createProcessInstanceQuery().list()
                .forEach(p -> runtimeService.deleteProcessInstance(p.getId(), "Limpieza de test"));
    }

    @Test
    @Deployment(resources = {"procesos/organizarCurso.bpmn20.xml", "procesos/viabilidad.dmn"})
    void testViabilidadAceptadaYRechazada(RuntimeService runtimeService, TaskService taskService, HistoryService historyService) {
        // 1. Simular 4 cursos en ejecución con accesoHabilitado = true
        for (int i = 0; i < 4; i++) {
            runtimeService.startProcessInstanceByMessage("solicitudCapacitacion",
                    Map.of("accesoHabilitado", true, "plazoRespuesta", "P5D"));
        }

        // 2. Iniciar la 5ta solicitud: debe ser VIABLE (4 < 5)
        var instViable = runtimeService.startProcessInstanceByMessage("solicitudCapacitacion",
                Map.of("plazoRespuesta", "P5D"));

        Task tareaPropuesta = taskService.createTaskQuery()
                .processInstanceId(instViable.getId())
                .taskDefinitionKey("registrarYElaborarPropuesta")
                .singleResult();
        assertNotNull(tareaPropuesta, "Con 4 cursos activos, la solicitud debe ser viable y llegar a elaborar propuesta");

        // 3. Crear 1 curso activo más para sumar 5 cursos en ejecución (accesoHabilitado = true)
        runtimeService.startProcessInstanceByMessage("solicitudCapacitacion",
                Map.of("accesoHabilitado", true, "plazoRespuesta", "P5D"));

        // 4. Iniciar la 6ta solicitud: debe ser NO VIABLE (5 >= 5)
        var instNoViable = runtimeService.startProcessInstanceByMessage("solicitudCapacitacion",
                Map.of("plazoRespuesta", "P5D"));

        // La instancia debe haber finalizado en finNoViable con resultado = 'NO_VIABLE'
        var instFinalizada = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instNoViable.getId())
                .includeProcessVariables()
                .singleResult();

        assertNotNull(instFinalizada.getEndTime(), "El proceso no viable debe finalizar");
        assertEquals("NO_VIABLE", instFinalizada.getProcessVariables().get("resultado"));
    }

    @Test
    @Deployment(resources = {"procesos/organizarCurso.bpmn20.xml", "procesos/viabilidad.dmn"})
    void testModificacionesYNuevaPropuesta(RuntimeService runtimeService, TaskService taskService) {
        var processInstance = runtimeService.startProcessInstanceByMessage("solicitudCapacitacion",
                Map.of("plazoRespuesta", "P5D"));

        // Registrar y elaborar propuesta v1
        Task tarea = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        taskService.complete(tarea.getId(), Map.of(
                "programa", "Java 21 Avanzado",
                "presupuesto", 150000L,
                "fechasPosibles", "Mayo 2026"
        ));

        // Verificar versión 1
        assertEquals(1L, ((Number) runtimeService.getVariable(processInstance.getId(), "versionPropuesta")).longValue());

        // Simular que la empresa pide modificaciones (buscar la ejecución suscrita al mensaje)
        var ejecucionModif = runtimeService.createExecutionQuery()
                .processInstanceId(processInstance.getId())
                .messageEventSubscriptionName("modificacionesPropuesta")
                .singleResult();
        assertNotNull(ejecucionModif, "Debe existir una ejecución esperando el mensaje modificacionesPropuesta");

        runtimeService.messageEventReceived("modificacionesPropuesta", ejecucionModif.getId(),
                Map.of("observacionesEmpresa", "Bajar presupuesto"));

        // Debe volver a la tarea Revisar propuesta
        Task tareaRevisar = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .taskDefinitionKey("revisarPropuesta")
                .singleResult();
        assertNotNull(tareaRevisar);

        // Completar la revisión
        taskService.complete(tareaRevisar.getId(), Map.of(
                "programa", "Java 21 Avanzado",
                "presupuesto", 120000L,
                "fechasPosibles", "Mayo 2026"
        ));

        // Debe incrementarse a versión 2
        assertEquals(2L, ((Number) runtimeService.getVariable(processInstance.getId(), "versionPropuesta")).longValue());
    }

    @Test
    @Deployment(resources = {"procesos/organizarCurso.bpmn20.xml", "procesos/viabilidad.dmn"})
    void testRechazoDePropuesta(RuntimeService runtimeService, TaskService taskService, HistoryService historyService) {
        var processInstance = runtimeService.startProcessInstanceByMessage("solicitudCapacitacion",
                Map.of("plazoRespuesta", "P5D"));

        Task tarea = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        taskService.complete(tarea.getId(), Map.of("programa", "Soft Skills", "presupuesto", 80000L, "fechasPosibles", "Junio"));

        // Empresa envía mensaje de rechazo
        var ejecucionRechazo = runtimeService.createExecutionQuery()
                .processInstanceId(processInstance.getId())
                .messageEventSubscriptionName("rechazoPropuesta")
                .singleResult();
        assertNotNull(ejecucionRechazo, "Debe existir una ejecución esperando el mensaje rechazoPropuesta");

        runtimeService.messageEventReceived("rechazoPropuesta", ejecucionRechazo.getId());

        // Debe estar en la tarea Archivar solicitud
        Task tareaArchivar = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .taskDefinitionKey("archivarSolicitud")
                .singleResult();
        assertNotNull(tareaArchivar);

        // Completar archivo por parte del jefe admin
        taskService.complete(tareaArchivar.getId());

        // Verificar estado final
        var historicInst = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstance.getId())
                .includeProcessVariables()
                .singleResult();

        assertEquals("RECHAZADO", historicInst.getProcessVariables().get("resultado"));
    }

    @Test
    @Deployment(resources = {"procesos/organizarCurso.bpmn20.xml", "procesos/viabilidad.dmn"})
    void testFaltaDeRespuestaYRecordatorios(RuntimeService runtimeService, TaskService taskService, ManagementService managementService, HistoryService historyService) {
        var processInstance = runtimeService.startProcessInstanceByMessage("solicitudCapacitacion",
                Map.of("plazoRespuesta", "P5D"));

        Task tarea = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        taskService.complete(tarea.getId(), Map.of("programa", "Docker", "presupuesto", 100000L, "fechasPosibles", "Julio"));

        // 1er vencimiento de timer -> Envia recordatorio 1
        ejecutarTimerVencido(managementService, processInstance.getId());
        assertEquals(1L, ((Number) runtimeService.getVariable(processInstance.getId(), "recordatoriosEnviados")).longValue());

        // 2do vencimiento de timer -> Envia recordatorio 2
        ejecutarTimerVencido(managementService, processInstance.getId());
        assertEquals(2L, ((Number) runtimeService.getVariable(processInstance.getId(), "recordatoriosEnviados")).longValue());

        // 3er vencimiento de timer -> Como ya son 2 recordatorios, deriva a Archivar solicitud
        ejecutarTimerVencido(managementService, processInstance.getId());

        Task tareaArchivar = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .taskDefinitionKey("archivarSolicitud")
                .singleResult();
        assertNotNull(tareaArchivar, "Debe solicitar archivar al superar los recordatorios");

        taskService.complete(tareaArchivar.getId());

        var historicInst = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstance.getId())
                .includeProcessVariables()
                .singleResult();

        assertEquals("SIN_RESPUESTA", historicInst.getProcessVariables().get("resultado"));
    }

    private void ejecutarTimerVencido(ManagementService managementService, String processInstanceId) {
        var timerJob = managementService.createTimerJobQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        assertNotNull(timerJob, "Debe existir un timer esperando respuesta");

        var executableJob = managementService.moveTimerToExecutableJob(timerJob.getId());
        managementService.executeJob(executableJob.getId());
    }
}
