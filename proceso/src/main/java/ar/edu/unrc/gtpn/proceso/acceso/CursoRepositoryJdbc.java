package ar.edu.unrc.gtpn.proceso.acceso;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Implementación JDBC de la persistencia del resultado final del curso. */
public final class CursoRepositoryJdbc implements CursoRepository {

    private static final String ACTUALIZACION = "UPDATE curso SET estado = ?, programa = COALESCE(?, programa),"
            + " presupuesto = COALESCE(?, presupuesto), fecha_inicio = COALESCE(?, fecha_inicio),"
            + " informe_resultados = COALESCE(?, informe_resultados) WHERE id = ?";

    private final String url;
    private final String usuario;
    private final String clave;

    public CursoRepositoryJdbc(String url, String usuario, String clave) {
        this.url = url;
        this.usuario = usuario;
        this.clave = clave;
    }

    /** Configuración institucional usada por el servidor Flowable. */
    public static CursoRepositoryJdbc desdeEntorno() {
        return new CursoRepositoryJdbc(
                variable("INSTITUCION_DB_URL", "jdbc:postgresql://localhost:5432/institucion"),
                variable("INSTITUCION_DB_USER", "flowable"),
                variable("INSTITUCION_DB_PASSWORD", "flowable"));
    }

    @Override
    public void registrarResultado(String businessKey, DatosResultadoCurso datos) {
        final long cursoId;
        try {
            cursoId = Long.parseLong(businessKey);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("La businessKey no identifica un curso: " + businessKey, error);
        }
        try (Connection conexion = DriverManager.getConnection(url, usuario, clave);
                PreparedStatement actualizacion = conexion.prepareStatement(ACTUALIZACION)) {
            actualizacion.setString(1, datos.estado());
            actualizacion.setString(2, datos.programa());
            if (datos.presupuesto() == null) {
                actualizacion.setObject(3, null);
            } else {
                actualizacion.setBigDecimal(3, datos.presupuesto());
            }
            if (datos.fechaInicio() == null) {
                actualizacion.setObject(4, null);
            } else {
                actualizacion.setTimestamp(4, java.sql.Timestamp.valueOf(datos.fechaInicio()));
            }
            actualizacion.setString(5, datos.informeResultados());
            actualizacion.setLong(6, cursoId);
            actualizacion.executeUpdate();
        } catch (SQLException error) {
            throw new ErrorDeInstitucion("No se pudo registrar el resultado del curso " + cursoId, error);
        }
    }

    private static String variable(String nombre, String porDefecto) {
        String valor = System.getProperty(nombre);
        if (valor == null || valor.isBlank()) {
            valor = System.getenv(nombre);
        }
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }

    public static final class ErrorDeInstitucion extends RuntimeException {
        ErrorDeInstitucion(String mensaje, SQLException causa) {
            super(mensaje, causa);
        }

        ErrorDeInstitucion(String mensaje) {
            super(mensaje);
        }
    }
}
