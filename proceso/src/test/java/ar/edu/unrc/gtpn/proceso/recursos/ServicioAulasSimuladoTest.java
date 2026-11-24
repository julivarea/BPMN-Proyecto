package ar.edu.unrc.gtpn.proceso.recursos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifica la simulación del web service de aulas (issues #19 y #36). */
class ServicioAulasSimuladoTest {

    private final ServicioAulas simulado = new ServicioAulasSimulado();

    @BeforeEach
    void haySoloDosAulas() {
        ServicioAulasSimulado.reiniciar(2);
    }

    @Test
    void cadaReservaDevuelveUnAulaNumeradaDistinta() {
        String una = simulado.reservar("7");
        String otra = simulado.reservar("8");

        assertTrue(una.matches("Aula \\d+"), "el aula se identifica como \"Aula \" + número: " + una);
        assertNotEquals(una, otra, "dos reservas no pueden recibir la misma aula");
    }

    @Test
    void sinAulasLibresLanzaLaExcepcion() {
        simulado.reservar("7");
        simulado.reservar("8");

        assertThrows(AulaNoDisponible.class, () -> simulado.reservar("9"));
    }

    @Test
    void unAulaLiberadaVuelveAEstarDisponible() {
        String aula = simulado.reservar("7");
        simulado.reservar("8");

        simulado.liberar("7", aula);

        assertEquals(aula, simulado.reservar("9"));
    }
}
