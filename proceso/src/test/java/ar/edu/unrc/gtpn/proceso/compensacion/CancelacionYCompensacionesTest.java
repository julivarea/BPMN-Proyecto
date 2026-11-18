package ar.edu.unrc.gtpn.proceso.compensacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.test.Deployment;
import org.flowable.engine.test.FlowableTest;
import org.flowable.job.api.Job;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ar.edu.unrc.gtpn.proceso.acceso.CuentasDeEmpresa;
import ar.edu.unrc.gtpn.proceso.acceso.EmpresaDelCurso;
import ar.edu.unrc.gtpn.proceso.acceso.HabilitarAccesoEmpresaDelegate;

/**
 * Escenario 6 de la defensa (enunciado 1.3 y 1.4): la cancelación compensa solo los recursos ya gestionados y el
 * cierre normal libera todos antes de terminar. Si el editor de Flowable convierte una asociación de compensación en
 * flecha de secuencia (05-decisiones, limitación 5), estos tests fallan.
 */
@FlowableTest
class CancelacionYCompensacionesTest {

    private static final String PROCESO = "procesos/organizarCurso.bpmn20.xml";
    private static final String REGLA = "procesos/viabilidad.dmn";
    private static final Set<String> LIBERACIONES = Set.of("liberarAula", "liberarAulaVirtual", "liberarEquipamiento");

    private RuntimeService runtime;
    private TaskService tareas;
    private HistoryService historial;
    private ManagementService gestion;
    private String instancia;

    @BeforeEach
    void obtenerServicios(ProcessEngine motor) {
        runtime = motor.getRuntimeService();
        tareas = motor.getTaskService();
        historial = motor.getHistoryService();
        gestion = motor.getManagementService();
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
    void cancelarConElAulaReservadaYElEquipamientoSinContratarLiberaSoloElAula() {
        llegarAPreparacion();
        evaluarRecursos(true, false, true);

        recibirMensaje("cancelacionCurso", Map.of());

        Set<String> ejecutadas = actividadesEjecutadas();
        assertTrue(ejecutadas.contains("liberarAula"));
        assertFalse(ejecutadas.contains("liberarEquipamiento"));
        assertTerminoCon("finCancelacion", "CANCELADO");
    }

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void cancelarSinRecursosGestionadosNoCompensaNada() {
        llegarAPreparacion();

        recibirMensaje("cancelacionCurso", Map.of());

        assertTrue(liberacionesEjecutadas().isEmpty());
        assertTerminoCon("finCancelacion", "CANCELADO");
    }

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void elCierreNormalLiberaLosRecursosGestionadosAntesDeTerminar() {
        llegarAPreparacion();
        recibirMensaje("nominaParticipantes", Map.of());
        completar("confeccionarListaDefinitiva", Map.of());
        completar("prepararMateriales", Map.of());
        evaluarRecursos(true, true, false);
        completar("solicitarAulaVirtual", Map.of());
        ejecutarJobsPendientes();
        completar("definirDocente", Map.of("docenteAsignado", "docente1"));
        ejecutarJobsPendientes();
        completar("desarrollarCurso", Map.of());
        completar("elaborarInformeResultados", Map.of());

        completar("enviarInformeYArchivarExpediente", Map.of());

        assertEquals(Set.of("liberarAula", "liberarAulaVirtual"), liberacionesEjecutadas());
        assertTerminoCon("finCursoFinalizado", "FINALIZADO");
    }

    /** Negociación aceptada: la instancia queda dentro de "Preparar curso", con las tres ramas activas. */
    private void llegarAPreparacion() {
        // El businessKey es el id del curso en la BD de la institución (docs/07-base-de-datos.md §3).
        instancia = runtime.startProcessInstanceByMessage(
                "solicitudCapacitacion", "7", Map.of("plazoRespuesta", "P5D")).getId();
        completar("registrarYElaborarPropuesta", Map.of());
        // Fecha ya pasada: el timer "fecha programada" vence apenas se crea.
        recibirMensaje("aceptacionPropuesta", Map.of("fechaInicioCurso", "2020-01-01T09:00:00"));
    }

    private void evaluarRecursos(boolean aula, boolean aulaVirtual, boolean equipamiento) {
        completar("evaluarRecursos", Map.of(
                "requiereAula", aula, "requiereAulaVirtual", aulaVirtual, "requiereEquipamiento", equipamiento));
    }

    private void completar(String tarea, Map<String, Object> variables) {
        Task pendiente = tareas.createTaskQuery()
                .processInstanceId(instancia).taskDefinitionKey(tarea).singleResult();
        assertNotNull(pendiente, "No hay una tarea pendiente " + tarea);
        tareas.complete(pendiente.getId(), variables);
    }

    private void recibirMensaje(String mensaje, Map<String, Object> variables) {
        Execution suscripta = runtime.createExecutionQuery()
                .processInstanceId(instancia).messageEventSubscriptionName(mensaje).singleResult();
        assertNotNull(suscripta, "La instancia no espera el mensaje " + mensaje);
        runtime.messageEventReceived(mensaje, suscripta.getId(), variables);
    }

    /** En los tests no corre el ejecutor en segundo plano: los timers y las tareas asíncronas se ejecutan a mano. */
    private void ejecutarJobsPendientes() {
        gestion.createTimerJobQuery().processInstanceId(instancia).list()
                .forEach(timer -> gestion.moveTimerToExecutableJob(timer.getId()));
        for (Job job : gestion.createJobQuery().processInstanceId(instancia).list()) {
            gestion.executeJob(job.getId());
        }
    }

    private Set<String> actividadesEjecutadas() {
        return historial.createHistoricActivityInstanceQuery().processInstanceId(instancia).list().stream()
                .map(HistoricActivityInstance::getActivityId)
                .collect(Collectors.toSet());
    }

    private Set<String> liberacionesEjecutadas() {
        return actividadesEjecutadas().stream().filter(LIBERACIONES::contains).collect(Collectors.toSet());
    }

    private void assertTerminoCon(String fin, String resultado) {
        HistoricProcessInstance terminada = historial.createHistoricProcessInstanceQuery()
                .processInstanceId(instancia).singleResult();
        assertNotNull(terminada.getEndTime(), "La instancia no terminó");
        assertEquals(fin, terminada.getEndActivityId());
        assertEquals(resultado, historial.createHistoricVariableInstanceQuery()
                .processInstanceId(instancia).variableName("resultado").singleResult().getValue());
    }
}
