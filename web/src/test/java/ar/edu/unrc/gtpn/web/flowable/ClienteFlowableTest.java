package ar.edu.unrc.gtpn.web.flowable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Verifica las llamadas que arma el cliente contra un servidor HTTP simulado, sin Flowable ni Docker. */
class ClienteFlowableTest {

    private static final String URL = "http://flowable/process-api";

    private MockRestServiceServer servidor;
    private ClienteFlowable cliente;

    @BeforeEach
    void crearCliente() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new ClienteFlowable(builder, new FlowableProperties(URL, "admin", "test"));
    }

    @Test
    void iniciarPorMensajeEnviaElMensajeLaClaveYLasVariablesYDevuelveElIdDeLaInstancia() {
        servidor.expect(requestTo(URL + "/runtime/process-instances"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic YWRtaW46dGVzdA=="))
                .andExpect(content().json("""
                        {"message": "solicitudCapacitacion", "businessKey": "42",
                         "variables": [{"name": "plazoRespuesta", "value": "P5D"}]}"""))
                .andRespond(withSuccess("""
                        {"id": "instancia-1", "businessKey": "42"}""", MediaType.APPLICATION_JSON));

        String instancia = cliente.iniciarPorMensaje("solicitudCapacitacion", "42", Map.of("plazoRespuesta", "P5D"));

        assertEquals("instancia-1", instancia);
        servidor.verify();
    }

    @Test
    void tareasAsignadasDevuelveLasTareasDelUsuario() {
        servidor.expect(requestTo(URL + "/runtime/tasks?assignee=jefe_admin&size=500"))
                .andRespond(withSuccess("""
                        {"data": [{"id": "t1", "name": "Registrar y elaborar propuesta",
                                   "taskDefinitionKey": "registrarYElaborarPropuesta",
                                   "processInstanceId": "instancia-1", "assignee": "jefe_admin",
                                   "priority": 50}],
                         "total": 1}""", MediaType.APPLICATION_JSON));

        List<Tarea> tareas = cliente.tareasAsignadas("jefe_admin");

        assertEquals(List.of(new Tarea("t1", "Registrar y elaborar propuesta", "registrarYElaborarPropuesta",
                "instancia-1", "jefe_admin")), tareas);
    }

    @Test
    void tareasDisponiblesFiltraPorLosGruposDelUsuario() {
        servidor.expect(requestTo(URL + "/runtime/tasks?candidateUser=administrativo&size=500"))
                .andRespond(withSuccess("""
                        {"data": [], "total": 0}""", MediaType.APPLICATION_JSON));

        assertEquals(List.of(), cliente.tareasDisponibles("administrativo"));
    }

    @Test
    void reclamarAsignaLaTareaAlUsuario() {
        servidor.expect(requestTo(URL + "/runtime/tasks/t1"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"action": "claim", "assignee": "administrativo"}"""))
                .andRespond(withSuccess());

        cliente.reclamar("t1", "administrativo");

        servidor.verify();
    }

    @Test
    void completarEnviaLasVariablesDeLaTarea() {
        servidor.expect(requestTo(URL + "/runtime/tasks/t1"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"action": "complete", "variables": [{"name": "presupuesto", "value": 1000}]}"""))
                .andRespond(withSuccess());

        cliente.completar("t1", Map.of("presupuesto", 1000));

        servidor.verify();
    }

    @Test
    void variablesDevuelveLasVariablesDeLaInstanciaPorNombre() {
        servidor.expect(requestTo(URL + "/runtime/process-instances/instancia-1/variables"))
                .andRespond(withSuccess("""
                        [{"name": "resultado", "type": "string", "value": "EN_CURSO", "scope": "local"},
                         {"name": "versionPropuesta", "type": "integer", "value": 1, "scope": "local"}]""",
                        MediaType.APPLICATION_JSON));

        assertEquals(Map.of("resultado", "EN_CURSO", "versionPropuesta", 1), cliente.variables("instancia-1"));
    }
}
