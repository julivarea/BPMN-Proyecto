package ar.edu.unrc.gtpn.web.seguridad;

/**
 * Cuenta habilitada para que una empresa acceda al sitio.
 */
public record CuentaEmpresa(long empresaId, String usuario, String passwordHash) {
}
