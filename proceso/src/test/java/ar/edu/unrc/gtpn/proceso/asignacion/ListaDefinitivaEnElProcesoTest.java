package ar.edu.unrc.gtpn.proceso.asignacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.flowable.engine.HistoryService;
import org.flowable.engine.IdentityService;
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
 * *Confeccionar lista definitiva* dentro del proceso real: el listener la asigna al administrativo con menos tareas y,
 * al completarla, esa persona tiene que figurar en el historial, porque de ahí sale la consulta de tareas completadas
 * por persona (enunciado 4.3.3).
 */
@FlowableTest
class ListaDefinitivaEnElProcesoTest {

    private static final String PROCESO = "procesos/organizarCurso.bpmn20.xml";
    private static final String REGLA = "procesos/viabilidad.dmn";

    private static final String GRUPO = "administracion";
    private static final List<String> ADMINISTRATIVOS = List.of("administrativo", "jefe_admin");

    private RuntimeService runtime;
    private TaskService tareas;
    private HistoryService historial;
    private IdentityService identidad;
    private String instancia;
    /** Usuarios de otros tests que estaban en el grupo: se sacan mientras corre este y se devuelven al terminar. */
    private final List<String> ajenos = new ArrayList<>();

    @BeforeEach
    void preparar(ProcessEngine motor) {
        runtime = motor.getRuntimeService();
        tareas = motor.getTaskService();
        historial = motor.getHistoryService();
        identidad = motor.getIdentityService();
        // El motor en memoria se comparte entre los tests: sin tareas viejas y solo con estos dos en el grupo.
        tareas.createTaskQuery().list().forEach(t -> tareas.deleteTask(t.getId(), true));
        if (identidad.createGroupQuery().groupId(GRUPO).count() == 0) {
            identidad.saveGroup(identidad.newGroup(GRUPO));
        }
        for (String usuario : ADMINISTRATIVOS) {
            if (identidad.createUserQuery().userId(usuario).count() == 0) {
                identidad.saveUser(identidad.newUser(usuario));
            }
            if (identidad.createUserQuery().userId(usuario).memberOfGroup(GRUPO).count() == 0) {
                identidad.createMembership(usuario, GRUPO);
            }
        }
        identidad.createUserQuery().memberOfGroup(GRUPO).list().stream()
                .map(usuario -> usuario.getId())
                .filter(id -> !ADMINISTRATIVOS.contains(id))
                .forEach(id -> {
                    identidad.deleteMembership(id, GRUPO);
                    ajenos.add(id);
                });
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
    void dejarTodoComoEstaba() {
        HabilitarAccesoEmpresaDelegate.usarRepositorio(null);
        ajenos.forEach(id -> identidad.createMembership(id, GRUPO));
        ajenos.clear();
    }

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void conAmbosSinTareasLaListaVaAlPrimeroEnOrdenAlfabetico() {
        Task lista = recibirLaNomina();

        assertEquals("administrativo", lista.getAssignee());
    }

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void conUnaTareaYaAsignadaAUnoLaListaVaAlOtro() {
        Task ocupada = tareas.newTask();
        ocupada.setAssignee("administrativo");
        tareas.saveTask(ocupada);

        Task lista = recibirLaNomina();

        assertEquals("jefe_admin", lista.getAssignee());
    }

    @Test
    @Deployment(resources = {PROCESO, REGLA})
    void laPersonaAsignadaPorElListenerQuedaRegistradaEnElHistorial() {
        Task lista = recibirLaNomina();
        String asignada = lista.getAssignee();
        assertNotNull(asignada, "el listener tiene que asignar la tarea");

        tareas.complete(lista.getId(), Map.of("listaDefinitiva", "Ana\nBeto"));

        var historica = historial.createHistoricTaskInstanceQuery().taskId(lista.getId()).singleResult();
        assertEquals(asignada, historica.getAssignee(),
                "la persona asignada por el listener tiene que figurar en el historial");
    }

    /** Negocia y acepta la propuesta y recibe la nómina: queda creada *Confeccionar lista definitiva*. */
    private Task recibirLaNomina() {
        instancia = runtime.startProcessInstanceByMessage(
                "solicitudCapacitacion", "7", Map.of("plazoRespuesta", "P5D")).getId();
        completar("registrarYElaborarPropuesta");
        recibir("aceptacionPropuesta", Map.of("fechaInicioCurso", "2020-01-01T09:00:00"));
        recibir("nominaParticipantes", Map.of("participantes", java.util.List.of("Ana", "Beto")));
        Task lista = tareas.createTaskQuery().processInstanceId(instancia)
                .taskDefinitionKey("confeccionarListaDefinitiva").singleResult();
        assertNotNull(lista);
        return lista;
    }

    private void completar(String tarea) {
        Task pendiente = tareas.createTaskQuery().processInstanceId(instancia).taskDefinitionKey(tarea).singleResult();
        assertNotNull(pendiente, "No hay una tarea pendiente " + tarea);
        tareas.complete(pendiente.getId(), Map.of("programa", "p", "presupuesto", 1L, "fechasPosibles", "f"));
    }

    private void recibir(String mensaje, Map<String, Object> variables) {
        Execution suscripta = runtime.createExecutionQuery()
                .processInstanceId(instancia).messageEventSubscriptionName(mensaje).singleResult();
        assertNotNull(suscripta, "La instancia no espera el mensaje " + mensaje);
        runtime.messageEventReceived(mensaje, suscripta.getId(), variables);
    }
}
