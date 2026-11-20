package ar.edu.unrc.gtpn.proceso.acceso;

import java.sql.SQLException;

/** La BD de la institución no respondió o rechazó la operación. */
public final class ErrorDeInstitucion extends RuntimeException {

    ErrorDeInstitucion(String mensaje, SQLException causa) {
        super(mensaje, causa);
    }
}
