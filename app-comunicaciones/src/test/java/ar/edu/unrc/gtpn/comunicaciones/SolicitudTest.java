package ar.edu.unrc.gtpn.comunicaciones;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SolicitudTest {

    private static final Instant AHORA = Instant.parse("2026-11-08T10:00:00Z");

    @Test
    void sePresentaConElCursoYLaVersionDeLaPropuesta() {
        assertEquals("42 · Excel avanzado · v2", new Solicitud("i1", "42", AHORA, "Excel avanzado", 2).descripcion());
    }

    @Test
    void sinPropuestaEnviadaNoMuestraVersion() {
        assertEquals("42 · Excel avanzado", new Solicitud("i1", "42", AHORA, "Excel avanzado", 0).descripcion());
    }

    @Test
    void sinNombreDeCursoMuestraSoloLaClave() {
        assertEquals("42", new Solicitud("i1", "42", AHORA).descripcion());
    }
}
