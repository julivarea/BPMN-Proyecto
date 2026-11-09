package ar.edu.unrc.gtpn.proceso.viabilidad;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.test.Deployment;
import org.flowable.engine.test.FlowableTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifica el límite de cinco cursos habilitados para la regla de viabilidad. */
@FlowableTest
class ContarCursosEnEjecucionListenerTest {

    private static final String PROCESO = "procesos/organizarCurso.bpmn20.xml";
    private static final String REGLA = "procesos/viabilidad.dmn";

    private RuntimeService runtime;
    private HistoryService historial;

    @BeforeEach
    void obtenerServicios(ProcessEngine motor) {
        runtime = motor.getRuntimeService();
        historial = motor.getHistoryService();
    }

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void cuentaSoloOtrosCursosConAccesoHabilitado() {
        for (int i = 0; i < 5; i++) {
            String instancia = runtime.startProcessInstanceByMessage(
                    "solicitudCapacitacion", "curso-" + i, Map.of("plazoRespuesta", "P5D")).getId();
            runtime.setVariable(instancia, "accesoHabilitado", true);
        }

        String nuevaInstancia = runtime.startProcessInstanceByMessage(
                "solicitudCapacitacion", "curso-nuevo", Map.of("plazoRespuesta", "P5D")).getId();

        assertEquals("NO_VIABLE", historial.createHistoricVariableInstanceQuery()
                .processInstanceId(nuevaInstancia)
                .variableName("resultado")
                .singleResult()
                .getValue());
        assertEquals(Long.valueOf(5), historial.createHistoricVariableInstanceQuery()
                .processInstanceId(nuevaInstancia)
                .variableName("cursosEnEjecucion")
                .singleResult()
                .getValue());
    }
}
