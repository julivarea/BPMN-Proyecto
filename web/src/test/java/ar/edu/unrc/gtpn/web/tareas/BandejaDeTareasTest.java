package ar.edu.unrc.gtpn.web.tareas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.flowable.Tarea;

/** Reglas de la bandeja (A-06, 05-decisiones limitación 7), con un doble del cliente de Flowable. */
class BandejaDeTareasTest {

    private final ClienteFlowable flowable = mock(ClienteFlowable.class);
    private final BandejaDeTareas bandeja = new BandejaDeTareas(flowable);

    @Test
    void elAsignadoCompletaSuTarea() {
        when(flowable.tarea("t1")).thenReturn(tarea("t1", "archivarSolicitud", "jefe_admin"));

        bandeja.completar("jefe_admin", "t1", Map.of());

        verify(flowable).completar("t1", Map.of());
    }

    @Test
    void nadiePuedeCompletarLaTareaDeOtroUsuario() {
        when(flowable.tarea("t1")).thenReturn(tarea("t1", "archivarSolicitud", "jefe_admin"));

        var error = assertThrows(AccionNoPermitida.class, () -> bandeja.completar("administrativo", "t1", Map.of()));

        assertEquals("Solo el usuario asignado puede completar esta tarea.", error.getMessage());
        verify(flowable, never()).completar(any(), anyMap());
    }

    @Test
    void unaTareaDeGrupoSinReclamarNoSeCompleta() {
        when(flowable.tarea("t1")).thenReturn(tarea("t1", "evaluarRecursos", null));

        assertThrows(AccionNoPermitida.class, () -> bandeja.completar("administrativo", "t1", Map.of()));
        verify(flowable, never()).completar(any(), anyMap());
    }

    @Test
    void prepararMaterialesNoSeCompletaDesdeLaWeb() {
        when(flowable.tarea("t1")).thenReturn(tarea("t1", "prepararMateriales", "administrativo"));

        assertThrows(AccionNoPermitida.class, () -> bandeja.completar("administrativo", "t1", Map.of()));
        verify(flowable, never()).completar(any(), anyMap());
    }

    @Test
    void confirmarCompensacionesSeCompletaCuandoYaNoHayLiberacionesPendientes() {
        Tarea confirmar = tarea("t1", "confirmarCompensaciones", "administrativo");
        when(flowable.tarea("t1")).thenReturn(confirmar);
        when(flowable.tareasDeLaInstancia("instancia-1")).thenReturn(List.of(confirmar));

        bandeja.completar("administrativo", "t1", Map.of());

        verify(flowable).completar("t1", Map.of());
    }

    @Test
    void confirmarCompensacionesNoSeCompletaMientrasHayaLiberacionesPendientes() {
        Tarea confirmar = tarea("t1", "confirmarCompensaciones", "administrativo");
        when(flowable.tarea("t1")).thenReturn(confirmar);
        when(flowable.tareasDeLaInstancia("instancia-1")).thenReturn(List.of(confirmar,
                tarea("t2", "liberarEquipamiento", null)));

        var error = assertThrows(AccionNoPermitida.class, () -> bandeja.completar("administrativo", "t1", Map.of()));

        assertEquals("Todavía no se puede completar esta tarea. Primero hay que completar: Tarea liberarEquipamiento.",
                error.getMessage());
        verify(flowable, never()).completar(any(), anyMap());
    }

    @Test
    void soloConfirmarCompensacionesQuedaBloqueadaPorLasLiberaciones() {
        Tarea otra = tarea("t1", "archivarSolicitud", "jefe_admin");

        assertNull(bandeja.motivoDeBloqueo(otra));
        verify(flowable, never()).tareasDeLaInstancia(any());
    }

    @Test
    void siNoSePuedeConsultarLasLiberacionesNoSeBloqueaYDecideFlowable() {
        Tarea confirmar = tarea("t1", "confirmarCompensaciones", "administrativo");
        when(flowable.tareasDeLaInstancia("instancia-1"))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("sin conexión"));

        assertNull(bandeja.motivoDeBloqueo(confirmar));
    }

    @Test
    void unaTareaQueYaNoEstaPendienteSeInforma() {
        when(flowable.tarea("t1")).thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "", null, null, null));

        assertThrows(AccionNoPermitida.class, () -> bandeja.completar("jefe_admin", "t1", Map.of()));
    }

    @Test
    void siFlowableRechazaElCompletarSeInformaSinErrorDelSitio() {
        when(flowable.tarea("t1")).thenReturn(tarea("t1", "evaluarRecursos", "administrativo"));
        doThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "", null, null, null))
                .when(flowable).completar("t1", Map.of());

        assertThrows(AccionNoPermitida.class, () -> bandeja.completar("administrativo", "t1", Map.of()));
    }

    @Test
    void seReclamaUnaTareaDisponibleParaLosGruposDelUsuario() {
        when(flowable.tareasDisponibles("administrativo")).thenReturn(List.of(tarea("t1", "evaluarRecursos", null)));

        bandeja.reclamar("administrativo", "t1");

        verify(flowable).reclamar("t1", "administrativo");
    }

    @Test
    void noSeReclamaUnaTareaQueNoEstaDisponibleParaElUsuario() {
        when(flowable.tareasDisponibles("docente1")).thenReturn(List.of());

        assertThrows(AccionNoPermitida.class, () -> bandeja.reclamar("docente1", "t1"));
        verify(flowable, never()).reclamar(any(), any());
    }

    @Test
    void siOtroLaReclamoAntesSeInforma() {
        when(flowable.tareasDisponibles("administrativo")).thenReturn(List.of(tarea("t1", "evaluarRecursos", null)));
        doThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "", null, null, null))
                .when(flowable).reclamar("t1", "administrativo");

        var error = assertThrows(AccionNoPermitida.class, () -> bandeja.reclamar("administrativo", "t1"));

        assertEquals("Otro usuario reclamó la tarea antes que vos.", error.getMessage());
    }

    private static final OffsetDateTime CREADA = OffsetDateTime.parse("2026-11-09T10:00:00Z");

    private static Tarea tarea(String id, String clave, String asignado) {
        return new Tarea(id, "Tarea " + clave, clave, "instancia-1", asignado, CREADA, "42");
    }
}
