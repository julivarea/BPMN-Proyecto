package ar.edu.unrc.gtpn.web.empresa;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Cursos de una empresa para el portal (W-04). Todas las consultas filtran por {@code empresa_id}: es lo que garantiza
 * que una empresa no vea los cursos de otra (escenario 10).
 */
@Repository
public class CursosDeLaEmpresaRepository {

    private static final String COLUMNAS = """
            SELECT id, nombre, descripcion, participantes_estimados, fechas_preferidas,
                   programa, presupuesto, fecha_inicio, informe_resultados, estado,
                   process_instance_id, creado_en
            FROM curso
            """;

    private final JdbcClient jdbc;

    public CursosDeLaEmpresaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Los cursos de la empresa, del más nuevo al más viejo. */
    public List<CursoDeLaEmpresa> listar(long empresaId) {
        return jdbc.sql(COLUMNAS + " WHERE empresa_id = :empresaId ORDER BY creado_en DESC, id DESC")
                .param("empresaId", empresaId)
                .query(CursosDeLaEmpresaRepository::mapear)
                .list();
    }

    /** Un curso de la empresa. Vacío si el curso no existe o es de otra empresa. */
    public Optional<CursoDeLaEmpresa> buscar(long empresaId, long cursoId) {
        return jdbc.sql(COLUMNAS + " WHERE empresa_id = :empresaId AND id = :cursoId")
                .param("empresaId", empresaId)
                .param("cursoId", cursoId)
                .query(CursosDeLaEmpresaRepository::mapear)
                .optional();
    }

    private static CursoDeLaEmpresa mapear(ResultSet fila, int numero) throws SQLException {
        return new CursoDeLaEmpresa(
                fila.getLong("id"),
                fila.getString("nombre"),
                fila.getString("descripcion"),
                fila.getInt("participantes_estimados"),
                fila.getString("fechas_preferidas"),
                fila.getString("programa"),
                fila.getObject("presupuesto", BigDecimal.class),
                fila.getObject("fecha_inicio", LocalDateTime.class),
                fila.getString("informe_resultados"),
                fila.getString("estado"),
                fila.getString("process_instance_id"),
                fila.getObject("creado_en", LocalDateTime.class));
    }
}
