package ar.edu.unrc.gtpn.web.seguridad;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CuentaEmpresaRepositoryJdbc implements CuentaEmpresaRepository {

    private final JdbcClient jdbc;

    public CuentaEmpresaRepositoryJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CuentaEmpresa> buscarPorUsuario(String usuario) {
        return jdbc.sql("""
                SELECT empresa_id, usuario, password_hash
                FROM cuenta_empresa
                WHERE lower(usuario) = lower(:usuario)
                """)
                .param("usuario", usuario)
                .query((fila, numero) -> new CuentaEmpresa(
                        fila.getLong("empresa_id"),
                        fila.getString("usuario"),
                        fila.getString("password_hash")))
                .optional();
    }
}
