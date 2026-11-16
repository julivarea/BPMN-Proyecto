package ar.edu.unrc.gtpn.comunicaciones;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Cliente de la API REST de Flowable: el único lugar de la app que conoce URLs y JSON de Flowable (enunciado 4.2.2). */
class FlowableRest {

    // Flowable devuelve 10 resultados por defecto; 500 alcanza para el proyecto.
    private static final int MAXIMO_RESULTADOS = 500;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final String url;
    private final String autorizacion;

    /**
     * @param url base de la API REST, por ejemplo {@code http://localhost:8080/flowable-ui/process-api}
     */
    FlowableRest(String url, String usuario, String clave) {
        this.url = url;
        String credenciales = usuario + ":" + clave;
        this.autorizacion = "Basic " + Base64.getEncoder().encodeToString(credenciales.getBytes(StandardCharsets.UTF_8));
    }

    /** Conexión configurada con las variables de entorno, con valores por defecto para el entorno Docker. */
    static FlowableRest desdeVariablesDeEntorno() {
        return new FlowableRest(
                variableDeEntorno("FLOWABLE_URL", "http://localhost:8080/flowable-ui") + "/process-api",
                variableDeEntorno("FLOWABLE_USUARIO", "admin"),
                variableDeEntorno("FLOWABLE_CLAVE", "test"));
    }

    /** Solicitudes cuya instancia del proceso sigue en curso, de la más vieja a la más nueva. */
    List<Solicitud> solicitudesEnCurso() {
        JsonNode respuesta = enviar("GET", "/runtime/process-instances?processDefinitionKey=organizarCurso&size="
                + MAXIMO_RESULTADOS, null);
        List<Solicitud> solicitudes = new ArrayList<>();
        for (JsonNode instancia : respuesta.get("data")) {
            solicitudes.add(new Solicitud(
                    instancia.get("id").asText(),
                    instancia.path("businessKey").asText("(sin clave)"),
                    Instant.parse(instancia.get("startTime").asText())));
        }
        solicitudes.sort(Comparator.comparing(Solicitud::iniciada));
        return solicitudes;
    }

    /**
     * Ejecución de la instancia que está esperando ese mensaje, o vacío si no lo está esperando ahora.
     * Una lista vacía significa que la comunicación no está habilitada (X-04).
     */
    Optional<String> ejecucionEsperando(String instanciaId, String mensaje) {
        JsonNode respuesta = enviar("GET", "/runtime/executions?processInstanceId=" + instanciaId
                + "&messageEventSubscriptionName=" + mensaje, null);
        for (JsonNode ejecucion : respuesta.get("data")) {
            return Optional.of(ejecucion.get("id").asText());
        }
        return Optional.empty();
    }

    /** Entrega el mensaje a la ejecución y la destraba; las variables quedan en el proceso. */
    void enviarMensaje(String ejecucionId, String mensaje, Map<String, Object> variables) {
        List<Map<String, Object>> lista = new ArrayList<>();
        variables.forEach((nombre, valor) -> {
            Map<String, Object> variable = new LinkedHashMap<>();
            variable.put("name", nombre);
            if (valor instanceof List<?>) {
                variable.put("type", "json");
            }
            variable.put("value", valor);
            lista.add(variable);
        });
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("action", "messageEventReceived");
        cuerpo.put("messageName", mensaje);
        cuerpo.put("variables", lista);
        enviar("PUT", "/runtime/executions/" + ejecucionId, cuerpo);
    }

    /** Hace la llamada y devuelve el JSON de la respuesta. Falla con {@link ErrorDeFlowable} si no es un 2xx. */
    private JsonNode enviar(String metodo, String ruta, Object cuerpo) {
        try {
            HttpRequest pedido = HttpRequest.newBuilder(URI.create(url + ruta))
                    .header("Authorization", autorizacion)
                    .header("Content-Type", "application/json")
                    .method(metodo, cuerpo == null
                            ? BodyPublishers.noBody()
                            : BodyPublishers.ofString(json.writeValueAsString(cuerpo)))
                    .build();
            var respuesta = http.send(pedido, BodyHandlers.ofString());
            if (respuesta.statusCode() / 100 != 2) {
                throw new ErrorDeFlowable(metodo + " " + ruta + " respondió " + respuesta.statusCode() + ": "
                        + respuesta.body());
            }
            return respuesta.body().isEmpty() ? json.nullNode() : json.readTree(respuesta.body());
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo conectar con Flowable en " + url, error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private static String variableDeEntorno(String nombre, String valorPorDefecto) {
        return System.getenv().getOrDefault(nombre, valorPorDefecto);
    }

    /** Flowable respondió con un error (4xx o 5xx). */
    static final class ErrorDeFlowable extends RuntimeException {
        ErrorDeFlowable(String mensaje) {
            super(mensaje);
        }
    }
}
