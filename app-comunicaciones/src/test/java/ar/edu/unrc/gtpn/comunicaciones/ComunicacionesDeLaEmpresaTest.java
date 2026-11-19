package ar.edu.unrc.gtpn.comunicaciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Verifica las comunicaciones de la negociación (issue #16): qué queda habilitado según lo que la instancia espera y
 * qué mensaje y variables se envían, con un doble del cliente REST (sin Flowable ni Docker).
 */
class ComunicacionesDeLaEmpresaTest {

    private final FlowableFalso flowable = new FlowableFalso();
    private final ComunicacionesDeLaEmpresa comunicaciones = new ComunicacionesDeLaEmpresa(flowable);
    private final Solicitud solicitud = new Solicitud("i1", "42", Instant.parse("2026-11-08T10:00:00Z"));

    @Test
    void soloQuedaHabilitadaLaComunicacionQueLaInstanciaEspera() {
        flowable.esperar("aceptacionPropuesta", "e1");

        assertEquals(Set.of(Comunicacion.ACEPTACION), comunicaciones.habilitadas(solicitud));
        assertEquals("i1", flowable.instanciaConsultada);
    }

    @Test
    void sinMensajesEsperandoNingunaComunicacionQuedaHabilitada() {
        assertEquals(Set.of(), comunicaciones.habilitadas(solicitud));
    }

    @Test
    void laAceptacionEnviaLaFechaAcordadaEnIso() {
        flowable.esperar("aceptacionPropuesta", "e7");

        comunicaciones.enviar(solicitud, Comunicacion.ACEPTACION, "01/12/2026");

        assertEquals("e7", flowable.ejecucionUsada);
        assertEquals("aceptacionPropuesta", flowable.mensajeEnviado);
        assertEquals(Map.of("fechaInicioCurso", "2026-12-01T09:00:00"), flowable.variablesEnviadas);
    }

    @Test
    void lasModificacionesEnvianLasObservaciones() {
        flowable.esperar("modificacionesPropuesta", "e8");

        comunicaciones.enviar(solicitud, Comunicacion.MODIFICACIONES, "  Cambiar el temario  ");

        assertEquals("modificacionesPropuesta", flowable.mensajeEnviado);
        assertEquals(Map.of("observacionesEmpresa", "Cambiar el temario"), flowable.variablesEnviadas);
    }

    @Test
    void elRechazoNoLlevaDatos() {
        flowable.esperar("rechazoPropuesta", "e9");

        comunicaciones.enviar(solicitud, Comunicacion.RECHAZO, "");

        assertEquals("rechazoPropuesta", flowable.mensajeEnviado);
        assertEquals(Map.of(), flowable.variablesEnviadas);
    }

    @Test
    void laNominaSeHabilitaYEnviaLaListaDeParticipantes() {
        flowable.esperar("nominaParticipantes", "e10");

        assertEquals(Set.of(Comunicacion.NOMINA_PARTICIPANTES), comunicaciones.habilitadas(solicitud));
        comunicaciones.enviar(solicitud, Comunicacion.NOMINA_PARTICIPANTES,
                " Juan Perez\nMaria Lopez, Carlos Gomez ");

        assertEquals("e10", flowable.ejecucionUsada);
        assertEquals("nominaParticipantes", flowable.mensajeEnviado);
        assertEquals(Map.of("participantes", List.of("Juan Perez", "Maria Lopez", "Carlos Gomez")),
                flowable.variablesEnviadas);
    }

    @Test
    void laCancelacionSeHabilitaYNoLlevaVariables() {
        flowable.esperar("cancelacionCurso", "e11");

        assertEquals(Set.of(Comunicacion.CANCELACION_CURSO), comunicaciones.habilitadas(solicitud));
        comunicaciones.enviar(solicitud, Comunicacion.CANCELACION_CURSO, "");

        assertEquals("e11", flowable.ejecucionUsada);
        assertEquals("cancelacionCurso", flowable.mensajeEnviado);
        assertEquals(Map.of(), flowable.variablesEnviadas);
    }

    @Test
    void noSeEnviaUnaComunicacionQueLaInstanciaNoEspera() {
        flowable.esperar("rechazoPropuesta", "e9");

        var error = assertThrows(ComunicacionesDeLaEmpresa.ComunicacionNoHabilitada.class,
                () -> comunicaciones.enviar(solicitud, Comunicacion.ACEPTACION, "2026-12-01"));

        assertNull(flowable.mensajeEnviado);
        assertTrue(error.getMessage().contains("Aceptar propuesta"));
    }

    @Test
    void unaFechaQueNoSeEntiendeNoSeEnvia() {
        flowable.esperar("aceptacionPropuesta", "e7");

        assertThrows(IllegalArgumentException.class,
                () -> comunicaciones.enviar(solicitud, Comunicacion.ACEPTACION, "el 1 de diciembre"));

        assertNull(flowable.mensajeEnviado);
    }

    @Test
    void laFechaAcordadaSeAceptaEnLosFormatosHabituales() {
        LocalDate hoy = LocalDate.of(2026, 11, 1);
        assertEquals("2026-12-01T09:00:00", ComunicacionesDeLaEmpresa.fechaISO("2026-12-01", hoy));
        assertEquals("2026-12-01T09:00:00", ComunicacionesDeLaEmpresa.fechaISO("01/12/2026", hoy));
        assertEquals("2026-12-01T18:30:00", ComunicacionesDeLaEmpresa.fechaISO("01/12/2026 18:30", hoy));
        assertEquals("2026-12-01T18:30:00", ComunicacionesDeLaEmpresa.fechaISO("2026-12-01T18:30:00", hoy));
    }

    @Test
    void cadaComunicacionTieneSuMensajeDelModeloYLosDatosQueCorresponden() {
        assertEquals("aceptacionPropuesta", Comunicacion.ACEPTACION.mensaje());
        assertEquals("rechazoPropuesta", Comunicacion.RECHAZO.mensaje());
        assertEquals("modificacionesPropuesta", Comunicacion.MODIFICACIONES.mensaje());

        assertEquals(Optional.of("fechaInicioCurso"), Comunicacion.ACEPTACION.variable());
        assertEquals(Optional.of("observacionesEmpresa"), Comunicacion.MODIFICACIONES.variable());
        assertEquals(Optional.empty(), Comunicacion.RECHAZO.variable());
        assertTrue(Comunicacion.ACEPTACION.pedido().isPresent());
        assertTrue(Comunicacion.MODIFICACIONES.pedido().isPresent());
        assertTrue(Comunicacion.RECHAZO.pedido().isEmpty());
    }

        @Test
    void unaFechaDeInicioAnteriorAHoyNoSeAcepta() {
        LocalDate hoy = LocalDate.of(2026, 11, 19);

        var error = assertThrows(IllegalArgumentException.class,
                () -> ComunicacionesDeLaEmpresa.fechaISO("18/11/2026", hoy));

        assertEquals("La fecha de inicio no puede ser anterior a hoy.", error.getMessage());
    }

    @Test
    void laFechaDeHoySeAceptaAunqueSuHoraYaHayaPasado() {
        LocalDate hoy = LocalDate.of(2026, 11, 19);

        assertEquals("2026-11-19T09:00:00", ComunicacionesDeLaEmpresa.fechaISO("19/11/2026", hoy));
    }
    
    /** Doble del cliente REST: recuerda qué mensajes espera la instancia y qué se envió. */
    private static final class FlowableFalso extends FlowableRest {

        private final Map<String, String> esperando = new HashMap<>();
        private String instanciaConsultada;
        private String ejecucionUsada;
        private String mensajeEnviado;
        private Map<String, Object> variablesEnviadas;

        FlowableFalso() {
            super("http://localhost:0/process-api", "admin", "test");
        }

        void esperar(String mensaje, String ejecucion) {
            esperando.put(mensaje, ejecucion);
        }

        @Override
        Optional<String> ejecucionEsperando(String instanciaId, String mensaje) {
            instanciaConsultada = instanciaId;
            return Optional.ofNullable(esperando.get(mensaje));
        }

        @Override
        void enviarMensaje(String ejecucionId, String mensaje, Map<String, Object> variables) {
            ejecucionUsada = ejecucionId;
            mensajeEnviado = mensaje;
            variablesEnviadas = variables;
        }
    }
}
