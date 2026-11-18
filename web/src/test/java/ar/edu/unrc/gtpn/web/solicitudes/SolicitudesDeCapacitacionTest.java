package ar.edu.unrc.gtpn.web.solicitudes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.ResourceAccessException;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;

/** Registro de solicitudes (W-01, W-02, 05-decisiones), con dobles del repositorio y del cliente de Flowable. */
class SolicitudesDeCapacitacionTest {

    private static final String CUIT = "30123456789";

    private final SolicitudesRepository repositorio = mock(SolicitudesRepository.class);
    private final ClienteFlowable flowable = mock(ClienteFlowable.class);
    private final SolicitudesDeCapacitacion solicitudes =
            new SolicitudesDeCapacitacion(repositorio, flowable, new SolicitudesProperties("P5D"),
                    TransactionOperations.withoutTransaction());

    @Test
    void unaEmpresaNuevaSeRegistraConElCuitSoloConDigitos() {
        when(repositorio.empresaPorCuit(CUIT)).thenReturn(Optional.empty());
        when(repositorio.crearEmpresa(CUIT, "Acme SA")).thenReturn(1L);
        when(repositorio.crearCurso(eq(1L), any())).thenReturn(42L);

        solicitudes.solicitar(solicitud("30-12345678-9", "Acme SA"));

        verify(repositorio).crearEmpresa(CUIT, "Acme SA");
    }

    @Test
    void laSolicitudIniciaSuInstanciaConElCursoComoBusinessKeyYLaVincula() {
        when(repositorio.empresaPorCuit(CUIT)).thenReturn(Optional.of(new Empresa(1L, "Acme SA")));
        when(repositorio.crearCurso(eq(1L), any())).thenReturn(42L);
        when(flowable.iniciarPorMensaje(any(), any(), anyMap())).thenReturn("instancia-1");

        long cursoId = solicitudes.solicitar(solicitud(CUIT, "Acme SA"));

        assertEquals(42L, cursoId);
        verify(flowable).iniciarPorMensaje("solicitudCapacitacion", "42", Map.of("plazoRespuesta", "P5D", "nombreCurso", "Excel avanzado"));
        verify(repositorio).vincularInstancia(42L, "instancia-1");
    }

    @Test
    void unaEmpresaYaRegistradaNoSeDuplicaAunqueCambienMayusculasYEspacios() {
        when(repositorio.empresaPorCuit(CUIT)).thenReturn(Optional.of(new Empresa(1L, "Acme SA")));

        solicitudes.solicitar(solicitud("30-12345678-9", "  ACME SA "));

        verify(repositorio, never()).crearEmpresa(any(), any());
        verify(repositorio).crearCurso(eq(1L), any());
    }

    @Test
    void unCuitRegistradoConOtraRazonSocialSeRechazaSinIniciarElProceso() {
        when(repositorio.empresaPorCuit(CUIT)).thenReturn(Optional.of(new Empresa(1L, "Acme SA")));

        assertThrows(CuitDeOtraEmpresa.class, () -> solicitudes.solicitar(solicitud(CUIT, "Otra Empresa SA")));

        verify(repositorio, never()).crearCurso(anyLong(), any());
        verify(flowable, never()).iniciarPorMensaje(any(), any(), anyMap());
    }

    @Test
    void siFlowableNoRespondeLaSolicitudNoSeRegistra() {
        when(repositorio.empresaPorCuit(CUIT)).thenReturn(Optional.of(new Empresa(1L, "Acme SA")));
        when(repositorio.crearCurso(eq(1L), any())).thenReturn(42L);
        when(flowable.iniciarPorMensaje(any(), any(), anyMap())).thenThrow(new ResourceAccessException("sin respuesta"));

        assertThrows(SolicitudNoRegistrada.class, () -> solicitudes.solicitar(solicitud(CUIT, "Acme SA")));

        verify(repositorio, never()).vincularInstancia(anyLong(), any());
        verify(repositorio).eliminarCurso(42L);
    }

    @Test
    void elCursoSeConfirmaEnLaBaseAntesDeIniciarLaInstancia() {
        // Una instancia que termina al iniciarse (no viable) actualiza el curso desde el motor: tiene que existir ya.
        TransactionOperations transacciones = mock(TransactionOperations.class);
        when(transacciones.execute(any())).thenAnswer(
                llamada -> llamada.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        SolicitudesDeCapacitacion servicio = new SolicitudesDeCapacitacion(repositorio, flowable,
                new SolicitudesProperties("P5D"), transacciones);
        when(repositorio.empresaPorCuit(CUIT)).thenReturn(Optional.of(new Empresa(1L, "Acme SA")));
        when(repositorio.crearCurso(eq(1L), any())).thenReturn(42L);
        when(flowable.iniciarPorMensaje(any(), any(), anyMap())).thenReturn("instancia-1");

        servicio.solicitar(solicitud(CUIT, "Acme SA"));

        InOrder orden = inOrder(transacciones, flowable);
        orden.verify(transacciones).execute(any());
        orden.verify(flowable).iniciarPorMensaje(any(), any(), anyMap());
    }

    @Test
    void lasDescripcionesSoloConsultanLasBusinessKeysQueSonCursos() {
        when(repositorio.descripcionesPorCurso(List.of(42L))).thenReturn(Map.of(42L, "Acme SA · Excel avanzado"));

        Map<String, String> descripciones = solicitudes.descripciones(Arrays.asList("42", null, "manual", "42"));

        assertEquals(Map.of("42", "Acme SA · Excel avanzado"), descripciones);
    }

    @Test
    void sinBusinessKeysDeCursosNoSeConsultaLaBase() {
        Map<String, String> descripciones = solicitudes.descripciones(Arrays.asList(null, "manual"));

        assertEquals(Map.of(), descripciones);
        verify(repositorio, never()).descripcionesPorCurso(any());
    }

    private static SolicitudFormulario solicitud(String cuit, String razonSocial) {
        return new SolicitudFormulario(cuit, razonSocial, "Ana Pérez", "ana@acme.com", null, "Excel avanzado",
                "Curso para el área contable", 10, "Marzo, por la mañana");
    }
}