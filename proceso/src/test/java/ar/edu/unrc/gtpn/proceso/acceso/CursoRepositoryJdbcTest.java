package ar.edu.unrc.gtpn.proceso.acceso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifica el SQL de {@link CursoRepositoryJdbc} contra una base H2 con la tabla {@code curso} de la institución. */
class CursoRepositoryJdbcTest {

    private String url;
    private CursoRepositoryJdbc cursos;

    @BeforeEach
    void crearLaBase() throws SQLException {
        url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Connection conexion = DriverManager.getConnection(url, "sa", "");
                Statement sentencia = conexion.createStatement()) {
            sentencia.execute("CREATE TABLE curso (id BIGINT PRIMARY KEY, estado VARCHAR(30) NOT NULL,"
                    + " programa VARCHAR(500), presupuesto DECIMAL(12, 2), fecha_inicio TIMESTAMP,"
                    + " informe_resultados VARCHAR(2000))");
            sentencia.execute("INSERT INTO curso (id, estado, programa, presupuesto)"
                    + " VALUES (7, 'EN_CURSO', 'Programa de la propuesta', 90000)");
        }
        cursos = new CursoRepositoryJdbc(url, "sa", "");
    }

    @Test
    void elResultadoActualizaElEstadoYLosDatosDelCurso() throws SQLException {
        cursos.registrarResultado(7L, new DatosResultadoCurso("FINALIZADO", "Programa final",
                new BigDecimal("125000"), LocalDateTime.of(2026, 12, 1, 9, 0), "Curso aprobado"));

        Map<String, Object> fila = consultar(7);
        assertEquals("FINALIZADO", fila.get("estado"));
        assertEquals("Programa final", fila.get("programa"));
        assertEquals(0, new BigDecimal("125000").compareTo((BigDecimal) fila.get("presupuesto")));
        assertEquals(LocalDateTime.of(2026, 12, 1, 9, 0), ((Timestamp) fila.get("fecha_inicio")).toLocalDateTime());
        assertEquals("Curso aprobado", fila.get("informe_resultados"));
    }

    @Test
    void losDatosAusentesConservanLoYaRegistrado() throws SQLException {
        cursos.registrarResultado(7L, new DatosResultadoCurso("RECHAZADO", null, null, null, null));

        Map<String, Object> fila = consultar(7);
        assertEquals("RECHAZADO", fila.get("estado"));
        assertEquals("Programa de la propuesta", fila.get("programa"));
        assertEquals(0, new BigDecimal("90000").compareTo((BigDecimal) fila.get("presupuesto")));
    }

    @Test
    void siLaBaseNoResponde() {
        CursoRepositoryJdbc sinBase = new CursoRepositoryJdbc("jdbc:h2:tcp://localhost:1/no-existe", "sa", "");

        assertThrows(ErrorDeInstitucion.class,
                () -> sinBase.registrarResultado(7L, new DatosResultadoCurso("NO_VIABLE", null, null, null, null)));
    }

    private Map<String, Object> consultar(long id) throws SQLException {
        try (Connection conexion = DriverManager.getConnection(url, "sa", "");
                Statement sentencia = conexion.createStatement();
                ResultSet fila = sentencia.executeQuery("SELECT * FROM curso WHERE id = " + id)) {
            fila.next();
            Map<String, Object> columnas = new HashMap<>();
            for (int columna = 1; columna <= fila.getMetaData().getColumnCount(); columna++) {
                columnas.put(fila.getMetaData().getColumnLabel(columna).toLowerCase(), fila.getObject(columna));
            }
            return columnas;
        }
    }
}
