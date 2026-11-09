package ar.edu.unrc.gtpn.web.tareas;

/** El usuario intentó algo que las reglas de la bandeja no le permiten. El mensaje se muestra tal cual. */
public class AccionNoPermitida extends RuntimeException {

    public AccionNoPermitida(String mensaje) {
        super(mensaje);
    }

    public AccionNoPermitida(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
