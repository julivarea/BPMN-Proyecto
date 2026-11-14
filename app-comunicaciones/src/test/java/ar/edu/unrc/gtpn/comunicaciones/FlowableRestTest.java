package ar.edu.unrc.gtpn.comunicaciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Verifica las llamadas del cliente contra un servidor HTTP simulado (el del JDK), sin Flowable ni Docker. */
class FlowableRestTest {

    private HttpServer servidor;
    private HttpExchange ultimoPedido;
    private String cuerpoPedido;
    private int estado;
    private String respuesta;
    private FlowableRest flowable;

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", pedido -> {
            ultimoPedido = pedido;
            cuerpoPedido = new String(pedido.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] cuerpo = respuesta.getBytes(StandardCharsets.UTF_8);
            pedido.sendResponseHeaders(estado, cuerpo.length);
            pedido.getResponseBody().write(cuerpo);
            pedido.close();
        });
        servidor.start();
        String url = "http://localhost:" + servidor.getAddress().getPort() + "/process-api";
        flowable = new FlowableRest(url, "admin", "test");
    }

    @AfterEach
    void apagarServidor() {
        servidor.stop(0);
    }

    @Test
    void solicitudesEnCursoPideLasInstanciasDelProcesoConAutenticacionBasica() {
        responder(200, """
                {"data": [], "total": 0}""");

        flowable.solicitudesEnCurso();

        assertEquals("/process-api/runtime/process-instances?processDefinitionKey=organizarCurso&size=500",
                ultimoPedido.getRequestURI().toString());
        assertEquals("Basic YWRtaW46dGVzdA==", ultimoPedido.getRequestHeaders().getFirst("Authorization"));
    }

    @Test
    void solicitudesEnCursoDevuelveLasSolicitudesDeLaMasViejaALaMasNueva() {
        responder(200, """
                {"data": [
                  {"id": "i2", "businessKey": "43", "startTime": "2026-11-09T10:00:00.000Z", "suspended": false},
                  {"id": "i1", "businessKey": "42", "startTime": "2026-11-08T10:00:00.000Z", "suspended": false}],
                 "total": 2}""");

        assertEquals(List.of(
                new Solicitud("i1", "42", Instant.parse("2026-11-08T10:00:00Z")),
                new Solicitud("i2", "43", Instant.parse("2026-11-09T10:00:00Z"))), flowable.solicitudesEnCurso());
    }

    @Test
    void unaRespuestaDeErrorDeFlowableSeInformaConSuCodigo() {
        responder(401, "");

        var error = assertThrows(FlowableRest.ErrorDeFlowable.class, flowable::solicitudesEnCurso);

        assertTrue(error.getMessage().contains("401"));
    }

    @Test
    void ejecucionEsperandoPideLasEjecucionesQueEsperanElMensaje() {
        responder(200, """
                {"data": [{"id": "e1", "processInstanceId": "i1"}], "total": 1}""");

        assertEquals(Optional.of("e1"), flowable.ejecucionEsperando("i1", "aceptacionPropuesta"));
        assertEquals("/process-api/runtime/executions?processInstanceId=i1"
                + "&messageEventSubscriptionName=aceptacionPropuesta", ultimoPedido.getRequestURI().toString());
    }

    @Test
    void ejecucionEsperandoQuedaVaciaSiLaInstanciaNoEsperaElMensaje() {
        responder(200, """
                {"data": [], "total": 0}""");

        assertEquals(Optional.empty(), flowable.ejecucionEsperando("i1", "rechazoPropuesta"));
    }

    @Test
    void enviarMensajeEntregaElMensajeConSusVariables() throws IOException {
        responder(204, "");

        flowable.enviarMensaje("e7", "aceptacionPropuesta", Map.of("fechaInicioCurso", "2026-12-01T09:00:00"));

        assertEquals("PUT", ultimoPedido.getRequestMethod());
        assertEquals("/process-api/runtime/executions/e7", ultimoPedido.getRequestURI().toString());
        var cuerpo = new ObjectMapper().readTree(cuerpoPedido);
        assertEquals("messageEventReceived", cuerpo.get("action").asText());
        assertEquals("aceptacionPropuesta", cuerpo.get("messageName").asText());
        assertEquals("fechaInicioCurso", cuerpo.get("variables").get(0).get("name").asText());
        assertEquals("2026-12-01T09:00:00", cuerpo.get("variables").get(0).get("value").asText());
    }

    private void responder(int estado, String respuesta) {
        this.estado = estado;
        this.respuesta = respuesta;
    }
}
