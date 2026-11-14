package ar.edu.unrc.gtpn.proceso.recursos;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simulación del web service de aulas (issue #19): no hay conexión real, cada reserva devuelve la siguiente aula
 * de una numeración correlativa ("Aula 101", "Aula 102", …) y las llamadas quedan en el log.
 */
public class ServicioAulasSimulado implements ServicioAulas {

    private static final Logger log = LoggerFactory.getLogger(ServicioAulasSimulado.class);

    /** Siguiente número de aula; es estático para que dos reservas no reciban la misma. */
    private static final AtomicInteger SIGUIENTE = new AtomicInteger(101);

    @Override
    public String reservar(String solicitud) {
        String aula = "Aula " + SIGUIENTE.getAndIncrement();
        log.info("Web service de aulas: reservada el {} para la solicitud {}", aula, solicitud);
        return aula;
    }

    @Override
    public void liberar(String solicitud, String aula) {
        log.info("Web service de aulas: liberada el {} de la solicitud {}", aula, solicitud);
    }
}
