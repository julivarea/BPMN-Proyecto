package ar.edu.unrc.gtpn.proceso.recursos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simulación de la plataforma de aulas virtuales (issue #19): la liberación solo queda en el log. */
public class ServicioAulasVirtualesSimulado implements ServicioAulasVirtuales {

    private static final Logger log = LoggerFactory.getLogger(ServicioAulasVirtualesSimulado.class);

    @Override
    public void liberar(String solicitud) {
        log.info("Plataforma virtual: liberada el aula virtual de la solicitud {}", solicitud);
    }
}
