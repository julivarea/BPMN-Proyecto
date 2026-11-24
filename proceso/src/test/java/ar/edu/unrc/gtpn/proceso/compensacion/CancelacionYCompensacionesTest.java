package ar.edu.unrc.gtpn.proceso.compensacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import ar.edu.unrc.gtpn.proceso.recursos.ServicioAulasSimulado;

/**
 * Escenario 6 de la defensa (enunciado 1.3 y 1.4): la cancelación compensa solo los recursos ya gestionados, espera a
 * que se completen las compensaciones (incluida la tarea de usuario *Liberar equipamiento*) y recién entonces notifica
 * el cierre; el cierre normal libera todos los recursos antes de terminar. Si el editor de Flowable convierte una asociación de compensación en
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
        ServicioAulasSimulado.reiniciar(3); // cada test parte con todas las aulas libres
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
        completar("confirmarCompensaciones", Map.of());
        assertTerminoPasandoPor("finCancelacion", "CANCELADO");
    }

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void cancelarSinRecursosGestionadosNoCompensaNadaPeroPideConfirmar() {
        llegarAPreparacion();

        recibirMensaje("cancelacionCurso", Map.of());

        assertTrue(liberacionesEjecutadas().isEmpty());
        assertNotNull(tareaPendiente("confirmarCompensaciones"), "la confirmación siempre queda pendiente");
        assertFalse(actividadesEjecutadas().contains("notificarCierre"));
        completar("confirmarCompensaciones", Map.of());
        assertTerminoPasandoPor("finCancelacion", "CANCELADO");
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

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void confirmarLasCompensacionesAntesDeLiberarElEquipamientoNoSeAdmiteYNoNotificaElCierre() {
        cancelarConEquipamientoContratado();

        // Enunciado 1.3: el cierre se notifica una vez concluidas las compensaciones. Liberar el equipamiento es una
        // tarea de usuario: mientras esté pendiente, Flowable no deja cerrar el subproceso de cancelación.
        assertTrue(liberacionesEjecutadas().contains("liberarAula"), "el aula se libera sola");
        assertNotNull(tareaPendiente("liberarEquipamiento"), "queda pendiente liberar el equipamiento");
        assertThrows(RuntimeException.class, () -> completar("confirmarCompensaciones", Map.of()));
        assertNotNull(tareaPendiente("confirmarCompensaciones"), "la confirmación sigue pendiente");
        assertNotNull(tareaPendiente("liberarEquipamiento"));
        assertFalse(actividadesEjecutadas().contains("notificarCierre"), "no se notificó el cierre");
        assertNull(historial.createHistoricProcessInstanceQuery().processInstanceId(instancia).singleResult().getEndTime(),
                "la instancia sigue abierta");
    }

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void conElEquipamientoLiberadoLaConfirmacionCierraYElCierreSeNotificaAlFinal() {
        cancelarConEquipamientoContratado();

        completar("liberarEquipamiento", Map.of());

        assertNotNull(tareaPendiente("confirmarCompensaciones"), "liberar no cierra la cancelación: falta confirmar");
        assertFalse(actividadesEjecutadas().contains("notificarCierre"));
        java.util.Date antesDeConfirmar = new java.util.Date();
        completar("confirmarCompensaciones", Map.of());

        assertTerminoPasandoPor("finCancelacion", "CANCELADO");
        assertFalse(fin("notificarCierre", true).before(antesDeConfirmar),
                "el cierre se notifica después de liberar el equipamiento y confirmar");
    }

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void cancelarConLosTresRecursosGestionadosLiberaTodoEInterrumpeLasRamasPendientes() {
        llegarAPreparacion();
        recibirMensaje("nominaParticipantes", Map.of());
        evaluarRecursos(true, true, true);
        completar("solicitarAulaVirtual", Map.of());
        completar("contratarEquipamiento", Map.of());
        assertNotNull(tareaPendiente("confeccionarListaDefinitiva"));
        assertNotNull(tareaPendiente("prepararMateriales"));

        recibirMensaje("cancelacionCurso", Map.of());

        assertTrue(liberacionesEjecutadas().containsAll(Set.of("liberarAula", "liberarAulaVirtual")));
        assertNull(tareaPendiente("confeccionarListaDefinitiva"), "las ramas pendientes se interrumpen");
        assertNull(tareaPendiente("prepararMateriales"), "las ramas pendientes se interrumpen");
        assertNotNull(tareaPendiente("liberarEquipamiento"));
        completar("liberarEquipamiento", Map.of());
        completar("confirmarCompensaciones", Map.of());
        assertTerminoPasandoPor("finCancelacion", "CANCELADO");
    }

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void completarUnaCancelacionNoAfectaALaDeOtraInstancia() {
        cancelarConEquipamientoContratado();
        String primera = instancia;
        cancelarConEquipamientoContratado();
        String segunda = instancia;

        instancia = primera;
        completar("liberarEquipamiento", Map.of());
        completar("confirmarCompensaciones", Map.of());

        assertTerminoPasandoPor("finCancelacion", "CANCELADO");
        instancia = segunda;
        assertNotNull(tareaPendiente("liberarEquipamiento"), "la otra cancelación sigue esperando su liberación");
        assertNotNull(tareaPendiente("confirmarCompensaciones"));
        assertFalse(actividadesEjecutadas().contains("notificarCierre"));
    }

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void unaVezTerminadaLaPreparacionYaNoSeAceptaLaCancelacion() {
        llegarAPreparacion();
        recibirMensaje("nominaParticipantes", Map.of());
        completar("confeccionarListaDefinitiva", Map.of());
        completar("prepararMateriales", Map.of());
        evaluarRecursos(true, false, true);
        completar("contratarEquipamiento", Map.of());
        ejecutarJobsPendientes();

        // Enunciado 1.3: la cancelación es "durante Preparar curso"; ya concluida, no hay a qué cancelar.
        assertNull(runtime.createExecutionQuery().processInstanceId(instancia)
                .messageEventSubscriptionName("cancelacionCurso").singleResult());
        assertNotNull(tareaPendiente("definirDocente"));
    }

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void despuesDeCancelarNoSeAceptaUnaSegundaCancelacion() {
        cancelarConEquipamientoContratado();

        assertNull(runtime.createExecutionQuery().processInstanceId(instancia)
                .messageEventSubscriptionName("cancelacionCurso").singleResult());
    }

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void elCierreNormalConEquipamientoEsperaSuLiberacionYTerminaAlCompletarla() {
        llegarAPreparacion();
        recibirMensaje("nominaParticipantes", Map.of());
        completar("confeccionarListaDefinitiva", Map.of());
        completar("prepararMateriales", Map.of());
        evaluarRecursos(true, false, true);
        completar("contratarEquipamiento", Map.of());
        ejecutarJobsPendientes();
        completar("definirDocente", Map.of("docenteAsignado", "docente1"));
        ejecutarJobsPendientes();
        completar("desarrollarCurso", Map.of());
        completar("elaborarInformeResultados", Map.of());

        completar("enviarInformeYArchivarExpediente", Map.of());

        // Enunciado 1.4: el proceso concluye una vez completada la liberación.
        assertNotNull(tareaPendiente("liberarEquipamiento"));
        assertNull(historial.createHistoricProcessInstanceQuery().processInstanceId(instancia).singleResult().getEndTime());
        assertNull(tareaPendiente("confirmarCompensaciones"), "la confirmación es solo de la cancelación");

        completar("liberarEquipamiento", Map.of());

        assertTerminoPasandoPor("finCursoFinalizado", "FINALIZADO");
    }

    private void cancelarConEquipamientoContratado() {
        llegarAPreparacion();
        evaluarRecursos(true, false, true);
        completar("contratarEquipamiento", Map.of());
        recibirMensaje("cancelacionCurso", Map.of());
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

    private Task tareaPendiente(String tarea) {
        return tareas.createTaskQuery().processInstanceId(instancia).taskDefinitionKey(tarea).singleResult();
    }

    /** Inicio o fin de la última ejecución de una actividad. */
    private java.util.Date fin(String actividad, boolean inicio) {
        HistoricActivityInstance ejecucion = historial.createHistoricActivityInstanceQuery()
                .processInstanceId(instancia).activityId(actividad).list().get(0);
        assertNotNull(inicio ? ejecucion.getStartTime() : ejecucion.getEndTime(), "La actividad " + actividad + " no terminó");
        return inicio ? ejecucion.getStartTime() : ejecucion.getEndTime();
    }

    private Set<String> actividadesEjecutadas() {
        return historial.createHistoricActivityInstanceQuery().processInstanceId(instancia).list().stream()
                .map(HistoricActivityInstance::getActivityId)
                .collect(Collectors.toSet());
    }

    private Set<String> liberacionesEjecutadas() {
        return actividadesEjecutadas().stream().filter(LIBERACIONES::contains).collect(Collectors.toSet());
    }

    /**
     * Cuando la instancia termina al completarse una tarea de compensación, Flowable no informa cuál fue la última
     * actividad: se verifica que haya pasado por el fin esperado y que el resultado sea el correcto.
     */
    private void assertTerminoPasandoPor(String fin, String resultado) {
        HistoricProcessInstance terminada = historial.createHistoricProcessInstanceQuery()
                .processInstanceId(instancia).singleResult();
        assertNotNull(terminada.getEndTime(), "La instancia no terminó");
        assertTrue(actividadesEjecutadas().contains(fin), "No pasó por " + fin);
        assertEquals(resultado, historial.createHistoricVariableInstanceQuery()
                .processInstanceId(instancia).variableName("resultado").singleResult().getValue());
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
