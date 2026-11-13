package ar.edu.unrc.gtpn.web.solicitudes;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acceso a las tablas empresa y curso para registrar las solicitudes (enunciado
 * §3.2 y §4.1.1).
 */
@Repository
public class SolicitudesRepository {

    private final JdbcClient jdbc;

    public SolicitudesRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Empresa> empresaPorCuit(String cuit) {
        return jdbc.sql("SELECT id, razon_social FROM empresa WHERE cuit = :cuit")
                .param("cuit", cuit)
                .query((fila, numero) -> new Empresa(fila.getLong("id"), fila.getString("razon_social")))
                .optional();
    }

    long crearEmpresa(String cuit, String razonSocial) {
        return jdbc.sql("INSERT INTO empresa (cuit, razon_social) VALUES (:cuit, :razonSocial) RETURNING id")
                .param("cuit", cuit)
                .param("razonSocial", razonSocial)
                .query(Long.class)
                .single();
    }

    long crearCurso(long empresaId, SolicitudFormulario solicitud) {
        return jdbc.sql("""
                INSERT INTO curso (empresa_id, nombre, descripcion, participantes_estimados, fechas_preferidas,
                                   contacto_nombre, contacto_email, contacto_telefono)
                VALUES (:empresaId, :nombre, :descripcion, :participantes, :fechas,
                        :contactoNombre, :contactoEmail, :contactoTelefono)
                RETURNING id
                """)
                .param("empresaId", empresaId)
                .param("nombre", solicitud.nombre())
                .param("descripcion", solicitud.descripcion())
                .param("participantes", solicitud.participantesEstimados())
                .param("fechas", solicitud.fechasPreferidas())
                .param("contactoNombre", solicitud.contactoNombre())
                .param("contactoEmail", solicitud.contactoEmail())
                .param("contactoTelefono", solicitud.contactoTelefono())
                .query(Long.class)
                .single();
    }

    void vincularInstancia(long cursoId, String instanciaId) {
        jdbc.sql("UPDATE curso SET process_instance_id = :instanciaId WHERE id = :cursoId")
                .param("instanciaId", instanciaId)
                .param("cursoId", cursoId)
                .update();
    }

    /**
     * "Razón social · Curso" de cada curso, por su id. Los ids que no existen en la
     * base no aparecen.
     */
    Map<Long, String> descripcionesPorCurso(List<Long> cursoIds) {
        return jdbc.sql("""
                SELECT curso.id, empresa.razon_social, curso.nombre
                FROM curso JOIN empresa ON empresa.id = curso.empresa_id
                WHERE curso.id IN (:cursoIds)
                """)
                .param("cursoIds", cursoIds)
                .query((fila, numero) -> Map.entry(fila.getLong("id"),
                        fila.getString("razon_social") + " · " + fila.getString("nombre")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Optional<Curso> buscarPorId(long id) {
        return jdbc.sql("""
                SELECT c.id, c.empresa_id, e.razon_social, c.nombre, c.descripcion,
                       c.participantes_estimados, c.fechas_preferidas,
                       c.contacto_nombre, c.contacto_email, c.contacto_telefono
                FROM curso c
                JOIN empresa e ON e.id = c.empresa_id
                WHERE c.id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> new Curso(
                        rs.getLong("id"),
                        rs.getLong("empresa_id"),
                        rs.getString("razon_social"),
                        rs.getString("nombre"),
                        rs.getString("descripcion"),
                        rs.getInt("participantes_estimados"),
                        rs.getString("fechas_preferidas"),
                        rs.getString("contacto_nombre"),
                        rs.getString("contacto_email"),
                        rs.getString("contacto_telefono")))
                .optional();
    }
}
