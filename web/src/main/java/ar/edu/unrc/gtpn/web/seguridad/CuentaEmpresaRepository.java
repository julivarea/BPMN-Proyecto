package ar.edu.unrc.gtpn.web.seguridad;

import java.util.Optional;

/**
 * Consulta las cuentas de empresas de la base institucional.
 */
public interface CuentaEmpresaRepository {

    Optional<CuentaEmpresa> buscarPorUsuario(String usuario);
}
