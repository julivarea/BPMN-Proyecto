package ar.edu.unrc.gtpn.comunicaciones;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Prueba el cliente contra el Flowable del entorno Docker. No corre en el build normal ni en CI; se activa con
 * {@code FLOWABLE_DOCKER=si ./mvnw -pl app-comunicaciones verify} (con el entorno levantado).
 */
@EnabledIfEnvironmentVariable(named = "FLOWABLE_DOCKER", matches = "si")
class FlowableRestContraDockerTest {

    @Test
    void listaLasSolicitudesEnCurso() {
        System.out.println("Solicitudes en curso: " + FlowableRest.desdeVariablesDeEntorno().solicitudesEnCurso());
    }
}
