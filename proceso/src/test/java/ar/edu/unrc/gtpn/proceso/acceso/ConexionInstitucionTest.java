package ar.edu.unrc.gtpn.proceso.acceso;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** La configuración de la BD de la institución se toma de las propiedades del sistema o del entorno. */
class ConexionInstitucionTest {

    private static final String[] VARIABLES = {"INSTITUCION_DB_URL", "INSTITUCION_DB_USER", "INSTITUCION_DB_PASSWORD"};

    /** El pom define estas propiedades para toda la suite (docs/04): el test las deja como las encontró. */
    private final Map<String, String> originales = new HashMap<>();

    @BeforeEach
    void guardarPropiedades() {
        for (String variable : VARIABLES) {
            originales.put(variable, System.getProperty(variable));
        }
    }

    @AfterEach
    void restaurarPropiedades() {
        originales.forEach((variable, valor) -> {
            if (valor == null) {
                System.clearProperty(variable);
            } else {
                System.setProperty(variable, valor);
            }
        });
    }

    @Test
    void laPropiedadDelSistemaDefineLaConexion() throws SQLException {
        System.setProperty("INSTITUCION_DB_URL", "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        System.setProperty("INSTITUCION_DB_USER", "sa");
        System.setProperty("INSTITUCION_DB_PASSWORD", "");

        try (Connection conexion = ConexionInstitucion.desdeEntorno().abrir()) {
            assertDoesNotThrow(() -> conexion.createStatement().execute("SELECT 1"));
        }
    }
}
