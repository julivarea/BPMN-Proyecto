package ar.edu.unrc.gtpn.proceso.acceso;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Implementación JDBC de la persistencia del resultado final del curso. */
public final class CursoRepositoryJdbc implements CursoRepository {

    private static final String ACTUALIZACION = "UPDATE curso SET estado = ?, programa = COALESCE(?, programa),"
            + " presupuesto = COALESCE(?, presupuesto), fecha_inicio = COALESCE(?, fecha_inicio),"
            + " informe_resultados = COALESCE(?, informe_resultados) WHERE id = ?";

    private final ConexionInstitucion conexion;

    public CursoRepositoryJdbc(String url, String usuario, String clave) {
        this(new ConexionInstitucion(url, usuario, clave));
    }

    private CursoRepositoryJdbc(ConexionInstitucion conexion) {
        this.conexion = conexion;
    }

    /** Configuración institucional usada por el servidor Flowable. */
    public static CursoRepositoryJdbc desdeEntorno() {
        return new CursoRepositoryJdbc(ConexionInstitucion.desdeEntorno());
    }

    @Override
    public void registrarResultado(long cursoId, DatosResultadoCurso datos) {
        try (Connection abierta = conexion.abrir();
                PreparedStatement actualizacion = abierta.prepareStatement(ACTUALIZACION)) {
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
}
