package ar.edu.unrc.gtpn.proceso.acceso;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * {@link CuentasDeEmpresa} sobre la BD de la institución por JDBC (enunciado 4.1.2, issue #17).
 *
 * <p>El delegate corre dentro del servidor Flowable, que no es una aplicación Spring: por eso se usa
 * {@link DriverManager} y no un pool. La conexión la define el entorno del servidor
 * ({@code INSTITUCION_DB_*} en {@code docker-compose.yml}); el driver de PostgreSQL ya viene en la imagen.
 */
public final class CuentasDeEmpresaJdbc implements CuentasDeEmpresa {

    /** La empresa del curso y su cuenta, si existe: la cuenta es 0 o 1 por empresa (docs/07-base-de-datos.md). */
    private static final String CONSULTA = "SELECT e.id, e.razon_social, ce.usuario"
            + " FROM curso c"
            + " JOIN empresa e ON e.id = c.empresa_id"
            + " LEFT JOIN cuenta_empresa ce ON ce.empresa_id = e.id"
            + " WHERE c.id = ?";

    private static final String CREACION =
            "INSERT INTO cuenta_empresa (empresa_id, usuario, password_hash) VALUES (?, ?, ?)";

    private final String url;
    private final String usuario;
    private final String clave;

    public CuentasDeEmpresaJdbc(String url, String usuario, String clave) {
        this.url = url;
        this.usuario = usuario;
        this.clave = clave;
    }

    /** Configuración del servidor Flowable, con los mismos valores por defecto que {@code docker-compose.yml}. */
    public static CuentasDeEmpresaJdbc desdeVariablesDeEntorno() {
        return new CuentasDeEmpresaJdbc(
                variable("INSTITUCION_DB_URL", "jdbc:postgresql://localhost:5432/institucion"),
                variable("INSTITUCION_DB_USER", "flowable"),
                variable("INSTITUCION_DB_PASSWORD", "flowable"));
    }

    private static String variable(String nombre, String porDefecto) {
        String valor = System.getenv(nombre);
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }

    @Override
    public Optional<EmpresaDelCurso> buscarPorCurso(long cursoId) {
        try (Connection conexion = abrir();
                PreparedStatement consulta = conexion.prepareStatement(CONSULTA)) {
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
    public void crear(long empresaId, String usuario, String passwordHash) {
        try (Connection conexion = abrir();
                PreparedStatement creacion = conexion.prepareStatement(CREACION)) {
            creacion.setLong(1, empresaId);
            creacion.setString(2, usuario);
            creacion.setString(3, passwordHash);
            creacion.executeUpdate();
        } catch (SQLException error) {
            throw new ErrorDeInstitucion("No se pudo crear la cuenta de la empresa " + empresaId, error);
        }
    }

    private Connection abrir() throws SQLException {
        return DriverManager.getConnection(url, usuario, clave);
    }

    /** La BD de la institución no respondió o rechazó la operación. */
    public static final class ErrorDeInstitucion extends RuntimeException {

        ErrorDeInstitucion(String mensaje, SQLException causa) {
            super(mensaje, causa);
        }
    }
}
