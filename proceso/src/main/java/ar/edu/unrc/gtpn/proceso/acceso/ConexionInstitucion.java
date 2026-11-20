package ar.edu.unrc.gtpn.proceso.acceso;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Conexión a la BD de la institución (enunciado 3.2) desde el servidor Flowable, que no es una aplicación Spring: por
 * eso se usa {@link DriverManager} y no un pool. El driver de PostgreSQL ya viene en la imagen.
 */
final class ConexionInstitucion {

    private final String url;
    private final String usuario;
    private final String clave;

    ConexionInstitucion(String url, String usuario, String clave) {
        this.url = url;
        this.usuario = usuario;
        this.clave = clave;
    }

    /**
     * Toma {@code INSTITUCION_DB_URL}, {@code INSTITUCION_DB_USER} e {@code INSTITUCION_DB_PASSWORD} de las propiedades
     * del sistema o, si no están, del entorno del servidor, con los mismos valores por defecto que
     * {@code docker-compose.yml}.
     */
    static ConexionInstitucion desdeEntorno() {
        return new ConexionInstitucion(
                variable("INSTITUCION_DB_URL", "jdbc:postgresql://localhost:5432/institucion"),
                variable("INSTITUCION_DB_USER", "flowable"),
                variable("INSTITUCION_DB_PASSWORD", "flowable"));
    }

    Connection abrir() throws SQLException {
        return DriverManager.getConnection(url, usuario, clave);
    }

    private static String variable(String nombre, String porDefecto) {
        String valor = System.getProperty(nombre);
        if (valor == null || valor.isBlank()) {
            valor = System.getenv(nombre);
        }
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }
}
