package ar.edu.unrc.gtpn.materiales.historial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.test.Deployment;
import org.flowable.engine.test.FlowableTest;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Consultas históricas del §4.3, con un motor en memoria: resultados (#30), ramas (#31) y personas (#32). */
@FlowableTest
class ConsultasHistoricasTest {

    private static final String PREPARACION = "procesos/organizar-curso-preparacion-test.bpmn20.xml";

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

    @Test
    @Deployment(resources = PREPARACION)
    void unaInstanciaCanceladaDuranteLaPreparacionNoEntraEnElPromedio(RuntimeService runtime, TaskService tareas) {
        ProcessInstance instancia = iniciarPreparacion(runtime);
        completar(tareas, instancia.getId(), "prepararMateriales");
        recibir(runtime, instancia.getId(), "cancelacionCurso");

        ConsultasHistoricas.DuracionRamas duraciones = consultas.duracionPromedioDeLasRamas();

        assertEquals(new ConsultasHistoricas.DuracionRamas(
                Duration.ZERO, Duration.ZERO, Duration.ZERO, 0), duraciones);
    }

    @Test
    @Deployment(resources = PREPARACION)
    void promediaLasRamasDeLasInstanciasQueCompletaronLaPreparacion(RuntimeService runtime, TaskService tareas) {
        completarPreparacion(runtime, tareas);
        completarPreparacion(runtime, tareas);

        ConsultasHistoricas.DuracionRamas duraciones = consultas.duracionPromedioDeLasRamas();

        assertEquals(2, duraciones.totalInstancias());
        assertTrue(!duraciones.inscripciones().isNegative());
        assertTrue(!duraciones.materiales().isNegative());
        assertTrue(!duraciones.recursos().isNegative());
    }

    @Test
    void promediaLasTresRamasYDescartaLasInstanciasIncompletas() {
        List<ConsultasHistoricas.ActividadHistorica> actividades = List.of(
                actividad("a", "nominaRecibida", "2026-01-01T08:00:00Z", "2026-01-01T08:00:00Z"),
                actividad("a", "confeccionarListaDefinitiva", "2026-01-01T08:00:00Z", "2026-01-01T10:00:00Z"),
                actividad("a", "prepararMateriales", "2026-01-01T08:00:00Z", "2026-01-01T09:00:00Z"),
                actividad("a", "evaluarRecursos", "2026-01-01T08:00:00Z", "2026-01-01T08:30:00Z"),
                actividad("a", "reservarAula", "2026-01-01T08:30:00Z", "2026-01-01T11:00:00Z"),
                actividad("b", "nominaRecibida", "2026-01-02T08:00:00Z", "2026-01-02T08:00:00Z"),
                actividad("b", "confeccionarListaDefinitiva", "2026-01-02T08:00:00Z", "2026-01-02T09:00:00Z"),
                actividad("b", "prepararMateriales", "2026-01-02T08:00:00Z", "2026-01-02T10:00:00Z"),
                actividad("b", "evaluarRecursos", "2026-01-02T08:00:00Z", "2026-01-02T08:00:00Z"),
                actividad("b", "solicitarAulaVirtual", "2026-01-02T08:00:00Z", "2026-01-02T08:45:00Z"),
                actividad("b", "contratarEquipamiento", "2026-01-02T08:00:00Z", "2026-01-02T09:15:00Z"),
                // A la instancia "c" le falta el fin de inscripciones: se descarta.
                actividad("c", "nominaRecibida", "2026-01-03T08:00:00Z", "2026-01-03T08:00:00Z"),
                actividad("c", "prepararMateriales", "2026-01-03T08:00:00Z", "2026-01-03T09:00:00Z"),
                actividad("c", "evaluarRecursos", "2026-01-03T08:00:00Z", "2026-01-03T09:00:00Z"),
                actividad("c", "reservarAula", "2026-01-03T09:00:00Z", "2026-01-03T10:00:00Z"));

        assertEquals(new ConsultasHistoricas.DuracionRamas(
                        Duration.ofMinutes(90), Duration.ofMinutes(90), Duration.ofSeconds(7650), 2),
                ConsultasHistoricas.promediar(actividades));
    }

    @Test
    void sinActividadesDevuelvePromediosEnCero() {
        assertEquals(new ConsultasHistoricas.DuracionRamas(Duration.ZERO, Duration.ZERO, Duration.ZERO, 0),
                ConsultasHistoricas.promediar(List.of()));
    }

    @Test
    @Deployment(resources = "procesos/organizar-curso-test.bpmn20.xml")
    void cuentaLasTareasCompletadasPorUsuarioYOrdena(RuntimeService runtime, TaskService tareas) {
        completarComo(runtime, tareas, "jefe_admin");
        completarComo(runtime, tareas, "jefe_admin");
        completarComo(runtime, tareas, "docente1");
        completarComo(runtime, tareas, "administrativo2");
        // Sin assignee no se puede atribuir a ninguna persona.
        ProcessInstance sinAsignar = runtime.startProcessInstanceByKey(ConsultasHistoricas.PROCESO);
        tareas.complete(tareas.createTaskQuery().processInstanceId(sinAsignar.getId()).singleResult().getId());
        // Una tarea asignada pero todavía pendiente tampoco cuenta.
        ProcessInstance pendiente = runtime.startProcessInstanceByKey(ConsultasHistoricas.PROCESO);
        Task tareaPendiente = tareas.createTaskQuery().processInstanceId(pendiente.getId()).singleResult();
        tareas.setAssignee(tareaPendiente.getId(), "docente1");

        assertEquals(List.of(
                new ConsultasHistoricas.TareaPorPersona("jefe_admin", 2),
                new ConsultasHistoricas.TareaPorPersona("administrativo2", 1),
                new ConsultasHistoricas.TareaPorPersona("docente1", 1)),
                consultas.tareasCompletadasPorPersona());
    }

    @Test
    @Deployment(resources = PREPARACION)
    void unaTareaCanceladaNoCuentaComoCompletada(
            RuntimeService runtime, TaskService tareas, HistoryService historia) {
        ProcessInstance instancia = iniciarPreparacion(runtime);
        Task materiales = tareas.createTaskQuery()
                .processInstanceId(instancia.getId())
                .taskDefinitionKey("prepararMateriales")
                .singleResult();
        tareas.setAssignee(materiales.getId(), "jefe_admin");
        tareas.complete(materiales.getId());

        recibir(runtime, instancia.getId(), "nominaParticipantes");
        Task lista = tareas.createTaskQuery()
                .processInstanceId(instancia.getId())
                .taskDefinitionKey("confeccionarListaDefinitiva")
                .singleResult();
        tareas.setAssignee(lista.getId(), "docente1");

        recibir(runtime, instancia.getId(), "cancelacionCurso");

        HistoricTaskInstance cancelada = historia.createHistoricTaskInstanceQuery()
                .processInstanceId(instancia.getId())
                .taskDefinitionKey("confeccionarListaDefinitiva")
                .singleResult();
        assertNotNull(cancelada);
        assertNotNull(cancelada.getDeleteReason());
        assertTrue(!cancelada.getDeleteReason().isBlank());

        assertEquals(List.of(new ConsultasHistoricas.TareaPorPersona("jefe_admin", 1)),
                consultas.tareasCompletadasPorPersona());
    }

    private static void completarComo(RuntimeService runtime, TaskService tareas, String usuario) {
        ProcessInstance instancia = runtime.startProcessInstanceByKey(ConsultasHistoricas.PROCESO);
        Task tarea = tareas.createTaskQuery().processInstanceId(instancia.getId()).singleResult();
        tareas.setAssignee(tarea.getId(), usuario);
        tareas.complete(tarea.getId());
    }

    private static ProcessInstance iniciarPreparacion(RuntimeService runtime) {
        return runtime.startProcessInstanceByKey(ConsultasHistoricas.PROCESO,
                Map.of("requiereAula", true, "requiereAulaVirtual", false, "requiereEquipamiento", false));
    }

    private static void completarPreparacion(RuntimeService runtime, TaskService tareas) {
        ProcessInstance instancia = iniciarPreparacion(runtime);
        recibir(runtime, instancia.getId(), "nominaParticipantes");
        completar(tareas, instancia.getId(), "confeccionarListaDefinitiva");
        completar(tareas, instancia.getId(), "prepararMateriales");
        completar(tareas, instancia.getId(), "evaluarRecursos");
        completar(tareas, instancia.getId(), "reservarAula");
    }

    private static void recibir(RuntimeService runtime, String instancia, String mensaje) {
        Execution suscripta = runtime.createExecutionQuery()
                .processInstanceId(instancia)
                .messageEventSubscriptionName(mensaje)
                .singleResult();
        assertNotNull(suscripta, "La instancia no espera el mensaje " + mensaje);
        runtime.messageEventReceived(mensaje, suscripta.getId());
    }

    private static void completar(TaskService tareas, String instancia, String actividad) {
        Task tarea = tareas.createTaskQuery()
                .processInstanceId(instancia)
                .taskDefinitionKey(actividad)
                .singleResult();
        assertNotNull(tarea, "No hay tarea " + actividad + " en la instancia");
        tareas.complete(tarea.getId());
    }

    private static ConsultasHistoricas.ActividadHistorica actividad(
            String instancia, String actividad, String inicio, String fin) {
        return new ConsultasHistoricas.ActividadHistorica(
                instancia, actividad, Instant.parse(inicio), Instant.parse(fin));
    }
}
