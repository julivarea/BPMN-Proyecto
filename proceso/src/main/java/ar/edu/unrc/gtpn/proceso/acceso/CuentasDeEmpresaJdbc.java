package ar.edu.unrc.gtpn.proceso.acceso;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * {@link CuentasDeEmpresa} sobre la BD de la institución por JDBC (enunciado 4.1.2, issue #17).
 *
 * <p>El delegate corre dentro del servidor Flowable, que no es una aplicación Spring: la conexión la arma
 * {@link ConexionInstitucion} a partir del entorno del servidor ({@code INSTITUCION_DB_*} en
 * {@code docker-compose.yml}).
 */
public final class CuentasDeEmpresaJdbc implements CuentasDeEmpresa {

    /** La empresa del curso y su cuenta, si existe: la cuenta es 0 o 1 por empresa (docs/07-base-de-datos.md). */
    private static final String CONSULTA = "SELECT e.id, e.razon_social, ce.usuario"
            + " FROM curso c"
            + " JOIN empresa e ON e.id = c.empresa_id"
            + " LEFT JOIN cuenta_empresa ce ON ce.empresa_id = e.id"
            + " WHERE c.id = ?";

    private static final String EXISTE_USUARIO = "SELECT 1 FROM cuenta_empresa WHERE lower(usuario) = lower(?)";

    private static final String CREACION =
            "INSERT INTO cuenta_empresa (empresa_id, usuario, password_hash) VALUES (?, ?, ?)";

    private final ConexionInstitucion conexion;

    public CuentasDeEmpresaJdbc(String url, String usuario, String clave) {
        this(new ConexionInstitucion(url, usuario, clave));
    }

    private CuentasDeEmpresaJdbc(ConexionInstitucion conexion) {
        this.conexion = conexion;
    }

    /** Configuración institucional usada por el servidor Flowable. */
    public static CuentasDeEmpresaJdbc desdeEntorno() {
        return new CuentasDeEmpresaJdbc(ConexionInstitucion.desdeEntorno());
    }

    @Override
    public Optional<EmpresaDelCurso> buscarPorCurso(long cursoId) {
        try (Connection abierta = conexion.abrir();
                PreparedStatement consulta = abierta.prepareStatement(CONSULTA)) {
            consulta.setLong(1, cursoId);
            try (ResultSet fila = consulta.executeQuery()) {
                if (!fila.next()) {
                    return Optional.empty();
                }
                return Optional.of(new EmpresaDelCurso(
                        fila.getLong("id"), fila.getString("razon_social"), fila.getString("usuario")));
            }
        } catch (SQLException error) {
            throw new ErrorDeInstitucion("No se pudo consultar la empresa del curso " + cursoId, error);
        }
    }

    @Override
    public boolean existeUsuario(String usuario) {
        try (Connection abierta = conexion.abrir();
                PreparedStatement consulta = abierta.prepareStatement(EXISTE_USUARIO)) {
            consulta.setString(1, usuario);
            try (ResultSet fila = consulta.executeQuery()) {
                return fila.next();
            }
        } catch (SQLException error) {
            throw new ErrorDeInstitucion("No se pudo consultar el usuario " + usuario, error);
        }
    }

    @Override
    public void crear(long empresaId, String usuario, String passwordHash) {
        try (Connection abierta = conexion.abrir();
                PreparedStatement creacion = abierta.prepareStatement(CREACION)) {
            creacion.setLong(1, empresaId);
            creacion.setString(2, usuario);
            creacion.setString(3, passwordHash);
            creacion.executeUpdate();
        } catch (SQLException error) {
            throw new ErrorDeInstitucion("No se pudo crear la cuenta de la empresa " + empresaId, error);
        }
    }
}
