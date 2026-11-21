package ar.edu.unrc.gtpn.web.propuestas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PropuestasRepository {

    private final JdbcClient jdbc;

    public PropuestasRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Devuelve el número de la última versión registrada para un curso, o 0 si aún no tiene propuestas.
     */
    public int ultimaVersionDe(Long cursoId) {
        return jdbc.sql("SELECT COALESCE(MAX(version), 0) FROM propuesta WHERE curso_id = ?")
                .param(cursoId)
                .query(Integer.class)
                .single();
    }

    /**
     * Guarda una nueva versión de la propuesta en la base de datos.
     */
    public void guardar(Propuesta propuesta) {
        jdbc.sql("""
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
        return jdbc.sql("""
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
        return jdbc.sql("""
                SELECT id, curso_id, version, programa, presupuesto, fechas_posibles, observaciones_empresa, creada_en
                FROM propuesta
                WHERE curso_id = ?
                ORDER BY version ASC
                """)
                .param(cursoId)
                .query(this::mapearPropuesta)
                .list();
    }

    private Propuesta mapearPropuesta(ResultSet fila, int numero) throws SQLException {
        return new Propuesta(
                fila.getLong("id"),
                fila.getLong("curso_id"),
                fila.getInt("version"),
                fila.getString("programa"),
                fila.getBigDecimal("presupuesto"),
                fila.getString("fechas_posibles"),
                fila.getString("observaciones_empresa"),
                fila.getTimestamp("creada_en").toLocalDateTime()
        );
    }
}
