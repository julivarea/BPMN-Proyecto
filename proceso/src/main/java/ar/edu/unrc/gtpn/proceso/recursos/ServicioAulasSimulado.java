package ar.edu.unrc.gtpn.proceso.recursos;

import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simulación del web service de aulas (issue #19): no hay conexión real, pero sí un conjunto fijo de aulas
 * ("Aula 101", "Aula 102", …; {@value #CANTIDAD_POR_DEFECTO} por defecto, o las que indique la variable
 * {@code AULAS_DISPONIBLES} del servidor). Reservar toma una libre, liberar la devuelve y, si no queda ninguna,
 * se lanza {@link AulaNoDisponible} (issue #36). Las llamadas quedan en el log.
 */
public class ServicioAulasSimulado implements ServicioAulas {

    private static final Logger log = LoggerFactory.getLogger(ServicioAulasSimulado.class);

    static final int CANTIDAD_POR_DEFECTO = 10;

    /**
     * Números de las aulas libres; es estático para que dos reservas no reciban la misma. Flowable usa una instancia de
     * este servicio por delegate, así que el acceso se sincroniza sobre este conjunto y no sobre cada instancia.
     */
    private static final TreeSet<Integer> LIBRES = new TreeSet<>();

    static {
        reiniciar(cantidadConfigurada());
    }

    /** Vuelve a dejar libres las aulas 101 a 100 + {@code cantidad}; lo usan los tests. */
    public static void reiniciar(int cantidad) {
        synchronized (LIBRES) {
            LIBRES.clear();
            for (int numero = 101; numero < 101 + cantidad; numero++) {
                LIBRES.add(numero);
            }
        }
    }

    private static int cantidadConfigurada() {
        String valor = System.getProperty("AULAS_DISPONIBLES");
        if (valor == null || valor.isBlank()) {
            valor = System.getenv("AULAS_DISPONIBLES");
        }
        return valor == null || valor.isBlank() ? CANTIDAD_POR_DEFECTO : Integer.parseInt(valor.trim());
    }

    @Override
    public String reservar(String solicitud) {
        String aula;
        synchronized (LIBRES) {
            if (LIBRES.isEmpty()) {
                log.warn("Web service de aulas: no hay aulas libres para la solicitud {}", solicitud);
                throw new AulaNoDisponible("No hay aulas disponibles para la solicitud " + solicitud);
            }
            aula = "Aula " + LIBRES.pollFirst();
        }
        log.info("Web service de aulas: reservada el {} para la solicitud {}", aula, solicitud);
        return aula;
    }

    @Override
    public void liberar(String solicitud, String aula) {
        synchronized (LIBRES) {
            LIBRES.add(Integer.parseInt(aula.substring("Aula ".length())));
        }
        log.info("Web service de aulas: liberada el {} de la solicitud {}", aula, solicitud);
    }
}
