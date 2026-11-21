package ar.edu.unrc.gtpn.web.flowable;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * Prueba el cliente contra el Flowable del entorno Docker. No corre en el build normal ni en CI; se activa con
 * {@code FLOWABLE_DOCKER=si ./mvnw -pl web verify} (con el entorno levantado).
 */
@EnabledIfEnvironmentVariable(named = "FLOWABLE_DOCKER", matches = "si")
class ClienteFlowableContraDockerTest {

    @Test
    void listaLasTareasAsignadasAJefeAdmin() {
        var propiedades = new FlowableProperties("http://localhost:8080/flowable-ui", "admin", "test");
        var cliente = new ClienteFlowable(RestClient.builder(), propiedades);

        List<Tarea> tareas = cliente.tareasAsignadas("jefe_admin");

        System.out.println("Tareas de jefe_admin: " + tareas);
        assertTrue(tareas.stream().allMatch(tarea -> "jefe_admin".equals(tarea.asignado())));
    }
}
