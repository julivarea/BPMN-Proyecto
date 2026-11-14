package ar.edu.unrc.gtpn.proceso.acceso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifica el SQL de {@link CuentasDeEmpresaJdbc} contra una base H2 con las mismas tablas de la institución
 * (docs/07-base-de-datos.md §2). La conexión real es PostgreSQL; acá solo se prueba el acceso.
 */
class CuentasDeEmpresaJdbcTest {

    private CuentasDeEmpresaJdbc cuentas;

    @BeforeEach
    void crearLaBase() throws SQLException {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Connection conexion = DriverManager.getConnection(url, "sa", "");
                Statement sentencia = conexion.createStatement()) {
            sentencia.execute("CREATE TABLE empresa (id BIGINT PRIMARY KEY, cuit VARCHAR(13) NOT NULL UNIQUE,"
                    + " razon_social VARCHAR(200) NOT NULL)");
            sentencia.execute("CREATE TABLE cuenta_empresa (empresa_id BIGINT PRIMARY KEY REFERENCES empresa (id),"
                    + " usuario VARCHAR(100) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,"
                    + " creada_en TIMESTAMP NOT NULL DEFAULT now())");
            sentencia.execute("CREATE TABLE curso (id BIGINT PRIMARY KEY,"
                    + " empresa_id BIGINT NOT NULL REFERENCES empresa (id), nombre VARCHAR(200) NOT NULL)");
            sentencia.execute("INSERT INTO empresa (id, cuit, razon_social)"
                    + " VALUES (3, '30-11111111-1', 'Instituto ABC S.A.')");
            sentencia.execute("INSERT INTO curso (id, empresa_id, nombre)"
                    + " VALUES (7, 3, 'Curso de prueba'), (8, 3, 'Segundo curso')");
        }
        cuentas = new CuentasDeEmpresaJdbc(url, "sa", "");
    }

    @Test
    void laEmpresaSinCuentaSeEncuentraPorElCurso() {
        EmpresaDelCurso empresa = cuentas.buscarPorCurso(7).orElseThrow();

        assertEquals(3, empresa.id());
        assertEquals("Instituto ABC S.A.", empresa.razonSocial());
        assertFalse(empresa.tieneCuenta());
        assertEquals(Optional.empty(), empresa.usuario());
    }

    @Test
    void laCuentaCreadaQuedaVinculadaALaEmpresaYSeVeDesdeSusDosCursos() {
        cuentas.crear(3, "instituto-abc-s-a-3", "$2a$10$hash-de-prueba");

        assertEquals(Optional.of("instituto-abc-s-a-3"), cuentas.buscarPorCurso(7).orElseThrow().usuario());
        assertEquals(Optional.of("instituto-abc-s-a-3"), cuentas.buscarPorCurso(8).orElseThrow().usuario());
        assertTrue(cuentas.buscarPorCurso(7).orElseThrow().tieneCuenta());
    }

    @Test
    void unCursoInexistenteNoDevuelveEmpresa() {
        assertEquals(Optional.empty(), cuentas.buscarPorCurso(99));
    }
}
