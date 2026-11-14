package ar.edu.unrc.gtpn.proceso.acceso;

import java.util.Optional;

/**
 * Empresa dueña de un curso y, si ya contrató antes, su cuenta de acceso al sitio (enunciado 4.1.2).
 * La cuenta es 0 o 1 por empresa: se crea en la primera aceptación y se reutiliza en las siguientes.
 */
public final class EmpresaDelCurso {

    private final long id;
    private final String razonSocial;
    private final String usuario;

    public EmpresaDelCurso(long id, String razonSocial, String usuario) {
        this.id = id;
        this.razonSocial = razonSocial;
        this.usuario = usuario;
    }

    public long id() {
        return id;
    }

    public String razonSocial() {
        return razonSocial;
    }

    /** Usuario de la cuenta, si la empresa ya tiene una. */
    public Optional<String> usuario() {
        return Optional.ofNullable(usuario);
    }

    public boolean tieneCuenta() {
        return usuario != null;
    }
}
