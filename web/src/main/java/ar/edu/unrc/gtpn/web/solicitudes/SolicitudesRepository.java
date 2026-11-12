package ar.edu.unrc.gtpn.web.solicitudes;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acceso a las tablas empresa y curso para registrar las solicitudes (enunciado §3.2 y §4.1.1). */
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
}