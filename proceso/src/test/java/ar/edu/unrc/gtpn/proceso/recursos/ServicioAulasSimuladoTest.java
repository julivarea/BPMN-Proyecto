package ar.edu.unrc.gtpn.proceso.recursos;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Verifica la simulación del web service de aulas (issue #19). */
class ServicioAulasSimuladoTest {

    @Test
    void cadaReservaDevuelveUnAulaNumeradaDistinta() {
        ServicioAulas simulado = new ServicioAulasSimulado();

        String una = simulado.reservar("7");
        String otra = simulado.reservar("8");

        assertTrue(una.matches("Aula \\d+"), "el aula se identifica como \"Aula \" + número: " + una);
        assertNotEquals(una, otra, "dos reservas no pueden recibir la misma aula");
    }
}
