package ar.edu.unrc.gtpn.web.propuestas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PropuestasRepository {

    private final JdbcClient jdbcClient;

    public PropuestasRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Devuelve el número de la última versión registrada para un curso, o 0 si aún no tiene propuestas.
     */
    public int ultimaVersionDe(Long cursoId) {
        return jdbcClient.sql("SELECT COALESCE(MAX(version), 0) FROM propuesta WHERE curso_id = ?")
                .param(cursoId)
                .query(Integer.class)
                .single();
    }

    /**
     * Guarda una nueva versión de la propuesta en la base de datos.
     */
    public void guardar(Propuesta propuesta) {
        jdbcClient.sql("""
                INSERT INTO propuesta (curso_id, version, programa, presupuesto, fechas_posibles, observaciones_empresa)
                VALUES (?, ?, ?, ?, ?, ?)
                """)
                .param(propuesta.cursoId())
                .param(propuesta.version())
                .param(propuesta.programa())
                .param(propuesta.presupuesto())
                .param(propuesta.fechasPosibles())
                .param(propuesta.observacionesEmpresa())
                .update();
    }

    /**
     * Busca una versión específica de la propuesta de un curso.
     */
    public Optional<Propuesta> buscarPorCursoYVersion(Long cursoId, int version) {
        return jdbcClient.sql("""
                SELECT id, curso_id, version, programa, presupuesto, fechas_posibles, observaciones_empresa, creada_en
                FROM propuesta
                WHERE curso_id = ? AND version = ?
                """)
                .param(cursoId)
                .param(version)
                .query(this::mapearPropuesta)
                .optional();
    }

    /**
     * Recupera todas las versiones registradas para un curso ordenadas por versión.
     */
    public List<Propuesta> buscarPorCurso(Long cursoId) {
        return jdbcClient.sql("""
                SELECT id, curso_id, version, programa, presupuesto, fechas_posibles, observaciones_empresa, creada_en
                FROM propuesta
                WHERE curso_id = ?
                ORDER BY version ASC
                """)
                .param(cursoId)
                .query(this::mapearPropuesta)
                .list();
    }

    private Propuesta mapearPropuesta(ResultSet rs, int rowNum) throws SQLException {
        return new Propuesta(
                rs.getLong("id"),
                rs.getLong("curso_id"),
                rs.getInt("version"),
                rs.getString("programa"),
                rs.getBigDecimal("presupuesto"),
                rs.getString("fechas_posibles"),
                rs.getString("observaciones_empresa"),
                rs.getTimestamp("creada_en").toLocalDateTime()
        );
    }
}
