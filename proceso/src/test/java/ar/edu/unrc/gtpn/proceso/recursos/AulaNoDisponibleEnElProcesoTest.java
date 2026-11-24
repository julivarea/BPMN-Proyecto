package ar.edu.unrc.gtpn.proceso.recursos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;

import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.test.Deployment;
import org.flowable.engine.test.FlowableTest;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ar.edu.unrc.gtpn.proceso.acceso.CuentasDeEmpresa;
import ar.edu.unrc.gtpn.proceso.acceso.EmpresaDelCurso;
import ar.edu.unrc.gtpn.proceso.acceso.HabilitarAccesoEmpresaDelegate;

/**
 * Escenario 7 de la defensa (issue #36): con una sola aula, la segunda reserva falla, el modelo atrapa el error y
 * espera a que una persona confirme que hay un aula (*Confirmar aula disponible*), mientras las demás ramas siguen.
 */
@FlowableTest
class AulaNoDisponibleEnElProcesoTest {

    private static final String PROCESO = "procesos/organizarCurso.bpmn20.xml";
    private static final String REGLA = "procesos/viabilidad.dmn";

    private RuntimeService runtime;
    private TaskService tareas;
    private HistoryService historial;

    @BeforeEach
    void haySoloUnAula(ProcessEngine motor) {
        runtime = motor.getRuntimeService();
        tareas = motor.getTaskService();
        historial = motor.getHistoryService();
        ServicioAulasSimulado.reiniciar(1);
        // *Habilitar acceso* escribe en la BD de la institución, que acá no existe: se usa un doble.
        HabilitarAccesoEmpresaDelegate.usarRepositorio(new CuentasDeEmpresa() {
            @Override
            public Optional<EmpresaDelCurso> buscarPorCurso(long cursoId) {
                return Optional.of(new EmpresaDelCurso(1, "Empresa de prueba", null));
            }

            @Override
            public boolean existeUsuario(String usuario) {
                return false;
            }

            @Override
            public void crear(long empresaId, String usuario, String passwordHash) {
                // el acceso se habilita igual; la cuenta no se guarda en los tests del proceso
            }
        });
    }

    @AfterEach
    void quitarElRepositorioDePrueba() {
        HabilitarAccesoEmpresaDelegate.usarRepositorio(null);
    }

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void sinAulasElCursoEsperaConfirmacionYAlLiberarseUnaReintentaLaReserva() {
        String primero = prepararCursoConAula("7");
        String segundo = prepararCursoConAula("8");

        // El segundo curso no consiguió aula: queda esperando a una persona y no se le asignó ninguna.
        assertNotNull(tarea(segundo, "confirmarAulaDisponible"));
        assertNull(runtime.getVariable(segundo, "aulaAsignada"));
        assertNotNull(tarea(segundo, "prepararMateriales"), "las demás ramas de la preparación siguen");
        // El primero sí la tiene.
        assertEquals("Aula 101", runtime.getVariable(primero, "aulaAsignada"));

        // Se libera el aula (cancelando el primer curso) y la persona confirma: la reserva se reintenta.
        cancelar(primero);
        tareas.complete(tarea(primero, "confirmarCompensaciones").getId());
        tareas.complete(tarea(segundo, "confirmarAulaDisponible").getId());

        assertEquals("Aula 101", runtime.getVariable(segundo, "aulaAsignada"));
        assertNull(tarea(segundo, "confirmarAulaDisponible"));
    }

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void siSeConfirmaSinQueHayaAulaElErrorSeRepiteYLaTareaVuelveAEstarPendiente() {
        prepararCursoConAula("7");
        String segundo = prepararCursoConAula("8");

        tareas.complete(tarea(segundo, "confirmarAulaDisponible").getId());

        assertNotNull(tarea(segundo, "confirmarAulaDisponible"), "sigue sin haber aulas: se vuelve a pedir confirmación");
    }

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void cancelarMientrasEsperaElAulaNoLiberaNadaYCierraLaCancelacion() {
        prepararCursoConAula("7");
        String segundo = prepararCursoConAula("8");

        cancelar(segundo);

        assertNull(tarea(segundo, "confirmarAulaDisponible"), "la espera se interrumpe");
        assertTrue(historial.createHistoricActivityInstanceQuery().processInstanceId(segundo).activityId("liberarAula")
                .list().isEmpty(), "no había aula que liberar");
        tareas.complete(tarea(segundo, "confirmarCompensaciones").getId());
        assertEquals("CANCELADO", historial.createHistoricVariableInstanceQuery().processInstanceId(segundo)
                .variableName("resultado").singleResult().getValue());
    }

    /** Negociación aceptada y recursos evaluados: solo aula física. */
    private String prepararCursoConAula(String cursoId) {
        String instancia = runtime.startProcessInstanceByMessage(
                "solicitudCapacitacion", cursoId, Map.of("plazoRespuesta", "P5D")).getId();
        tareas.complete(tarea(instancia, "registrarYElaborarPropuesta").getId());
        // Fecha ya pasada: el timer "fecha programada" vence apenas se crea.
        recibirMensaje(instancia, "aceptacionPropuesta", Map.of("fechaInicioCurso", "2020-01-01T09:00:00"));
        tareas.complete(tarea(instancia, "evaluarRecursos").getId(), Map.of(
                "requiereAula", true, "requiereAulaVirtual", false, "requiereEquipamiento", false));
        return instancia;
    }

    private void cancelar(String instancia) {
        recibirMensaje(instancia, "cancelacionCurso", Map.of());
    }

    private void recibirMensaje(String instancia, String mensaje, Map<String, Object> variables) {
        Execution suscripta = runtime.createExecutionQuery()
                .processInstanceId(instancia).messageEventSubscriptionName(mensaje).singleResult();
        assertNotNull(suscripta, "La instancia no espera el mensaje " + mensaje);
        runtime.messageEventReceived(mensaje, suscripta.getId(), variables);
    }

    private Task tarea(String instancia, String definicion) {
        return tareas.createTaskQuery().processInstanceId(instancia).taskDefinitionKey(definicion).singleResult();
    }
}
