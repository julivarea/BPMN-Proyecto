package ar.edu.unrc.gtpn.web.solicitudes;

/** No se pudo iniciar el proceso de la solicitud; la transacción deshace lo guardado en la base. */
public class SolicitudNoRegistrada extends RuntimeException {

    public SolicitudNoRegistrada(Throwable causa) {
        super("No pudimos registrar la solicitud en este momento. Intentá de nuevo en unos minutos.", causa);
    }
}