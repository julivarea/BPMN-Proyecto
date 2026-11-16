package ar.edu.unrc.gtpn.materiales.historial;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.test.Deployment;
import org.flowable.engine.test.FlowableTest;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Resultados de las solicitudes (§4.3.1), con un motor en memoria (issue #30). */
@FlowableTest
class ConsultasHistoricasTest {

    private ConsultasHistoricas consultas;

    @BeforeEach
    void preparar(ProcessEngine motor) {
        consultas = new ConsultasHistoricas(motor.getHistoryService());
    }

    @Test
    @Deployment(resources = "procesos/organizar-curso-test.bpmn20.xml")
    void cuentaCadaResultadoYCalculaSuPorcentaje(RuntimeService runtime, TaskService tareas) {
        terminarConResultado(runtime, tareas, "FINALIZADO");
        terminarConResultado(runtime, tareas, "FINALIZADO");
        terminarConResultado(runtime, tareas, "RECHAZADO");
        terminarConResultado(runtime, tareas, "SIN_RESPUESTA");

        List<ConsultasHistoricas.ResultadoSolicitud> resultados = consultas.resultadosDeLasSolicitudes();

        assertEquals(List.of(
                new ConsultasHistoricas.ResultadoSolicitud("FINALIZADO", 2, 50.0),
                new ConsultasHistoricas.ResultadoSolicitud("RECHAZADO", 1, 25.0),
                new ConsultasHistoricas.ResultadoSolicitud("SIN_RESPUESTA", 1, 25.0)), resultados);
    }

    @Test
    @Deployment(resources = "procesos/organizar-curso-test.bpmn20.xml")
    void sinInstanciasTerminadasDevuelveUnaListaVacia(RuntimeService runtime) {
        // Una instancia en curso no entra en la consulta: solo cuentan las terminadas.
        runtime.startProcessInstanceByKey(ConsultasHistoricas.PROCESO, Map.of("resultado", "FINALIZADO"));

        assertEquals(List.of(), consultas.resultadosDeLasSolicitudes());
    }

    @Test
    @Deployment(resources = "procesos/organizar-curso-test.bpmn20.xml")
    void unaInstanciaSinResultadoNoRompeLaConsulta(RuntimeService runtime, TaskService tareas) {
        terminarConResultado(runtime, tareas, null);

        assertEquals(List.of(new ConsultasHistoricas.ResultadoSolicitud(
                        ConsultasHistoricas.SIN_RESULTADO, 1, 100.0)),
                consultas.resultadosDeLasSolicitudes());
    }

    private static void terminarConResultado(RuntimeService runtime, TaskService tareas, String resultado) {
        Map<String, Object> variables = resultado == null ? Map.of() : Map.of("resultado", resultado);
        ProcessInstance instancia = runtime.startProcessInstanceByKey(ConsultasHistoricas.PROCESO, variables);
        Task tarea = tareas.createTaskQuery().processInstanceId(instancia.getId()).singleResult();
        tareas.complete(tarea.getId());
    }
}
