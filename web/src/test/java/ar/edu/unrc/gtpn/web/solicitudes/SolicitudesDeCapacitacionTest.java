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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import ar.edu.unrc.gtpn.web.seguridad.CuentaEmpresa;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.flowable.InstanciaIniciada;
import ar.edu.unrc.gtpn.web.seguridad.CuentaEmpresaRepository;

/** Registro de solicitudes (W-01, W-02, 05-decisiones), con dobles del repositorio y del cliente de Flowable. */
class SolicitudesDeCapacitacionTest {

    private static final String CUIT = "30123456789";

    private final SolicitudesRepository repositorio = mock(SolicitudesRepository.class);
    private final CuentaEmpresaRepository cuentas = mock(CuentaEmpresaRepository.class);
    private final ClienteFlowable flowable = mock(ClienteFlowable.class);
    private final SolicitudesDeCapacitacion solicitudes = 
            new SolicitudesDeCapacitacion(repositorio, flowable, new SolicitudesProperties("P5D"), TransactionOperations.withoutTransaction(),cuentas);

    @Test
    void unaEmpresaNuevaSeRegistraConElCuitSoloConDigitos() {
        when(flowable.iniciarPorMensaje(any(), any(), anyMap())).thenReturn(new InstanciaIniciada("instancia-1", false));
        when(repositorio.empresaPorCuit(CUIT)).thenReturn(Optional.empty());
        when(repositorio.crearEmpresa(CUIT, "Acme SA")).thenReturn(1L);
        when(repositorio.crearCurso(eq(1L), any())).thenReturn(42L);

        solicitudes.solicitar(solicitud("30-12345678-9", "Acme SA"));

        verify(repositorio).crearEmpresa(CUIT, "Acme SA");
    }

    @Test
    void laSolicitudIniciaSuInstanciaConElCursoComoBusinessKeyYLaVincula() {
        when(repositorio.empresaPorCuit(CUIT)).thenReturn(Optional.of(new Empresa(1L, CUIT,"Acme SA")));
        when(repositorio.crearCurso(eq(1L), any())).thenReturn(42L);
        when(flowable.iniciarPorMensaje(any(), any(), anyMap())).thenReturn(new InstanciaIniciada("instancia-1", false));

        SolicitudRegistrada registrada = solicitudes.solicitar(solicitud(CUIT, "Acme SA"));

        assertEquals(new SolicitudRegistrada(42L, true), registrada);
        verify(flowable).iniciarPorMensaje("solicitudCapacitacion", "42", Map.of("plazoRespuesta", "P5D", "nombreCurso", "Excel avanzado"));
        verify(repositorio).vincularInstancia(42L, "instancia-1");
    }

    @Test
    void siElProcesoTerminaAlIniciarseLaSolicitudQuedaRegistradaPeroNoViable() {
        when(repositorio.empresaPorCuit(CUIT)).thenReturn(Optional.of(new Empresa(1L, CUIT,"Acme SA")));
        when(repositorio.crearCurso(eq(1L), any())).thenReturn(42L);
        when(flowable.iniciarPorMensaje(any(), any(), anyMap())).thenReturn(new InstanciaIniciada("instancia-9", true));

        SolicitudRegistrada registrada = solicitudes.solicitar(solicitud(CUIT, "Acme SA"));

        assertEquals(new SolicitudRegistrada(42L, false), registrada);
        verify(repositorio).vincularInstancia(42L, "instancia-9");
        verify(repositorio, never()).eliminarCurso(anyLong());
    }

    @Test
    void unaEmpresaYaRegistradaNoSeDuplicaAunqueCambienMayusculasYEspacios() {
        when(flowable.iniciarPorMensaje(any(), any(), anyMap())).thenReturn(new InstanciaIniciada("instancia-1", false));
        when(repositorio.empresaPorCuit(CUIT)).thenReturn(Optional.of(new Empresa(1L, CUIT,"Acme SA")));

        solicitudes.solicitar(solicitud("30-12345678-9", "  ACME SA "));

        verify(repositorio, never()).crearEmpresa(any(), any());
        verify(repositorio).crearCurso(eq(1L), any());
    }

    @Test
    void unCuitRegistradoConOtraRazonSocialSeRechazaSinIniciarElProceso() {
        when(repositorio.empresaPorCuit(CUIT)).thenReturn(Optional.of(new Empresa(1L, CUIT,"Acme SA")));

        assertThrows(CuitDeOtraEmpresa.class, () -> solicitudes.solicitar(solicitud(CUIT, "Otra Empresa SA")));

        verify(repositorio, never()).crearCurso(anyLong(), any());
        verify(flowable, never()).iniciarPorMensaje(any(), any(), anyMap());
    }

    @Test
    void siFlowableNoRespondeLaSolicitudNoSeRegistra() {
        when(repositorio.empresaPorCuit(CUIT)).thenReturn(Optional.of(new Empresa(1L, CUIT,"Acme SA")));
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
                new SolicitudesProperties("P5D"), transacciones, cuentas);
        when(repositorio.empresaPorCuit(CUIT)).thenReturn(Optional.of(new Empresa(1L, CUIT,"Acme SA")));
        when(repositorio.crearCurso(eq(1L), any())).thenReturn(42L);
        when(flowable.iniciarPorMensaje(any(), any(), anyMap())).thenReturn(new InstanciaIniciada("instancia-1", false));

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

        @Test
    void sinSesionNoHayEmpresaConSesion() {
        assertEquals(Optional.empty(), solicitudes.empresaConSesion(null));
    }

    @Test
    void elPersonalNoCuentaComoEmpresaConSesion() {
        var sesion = new UsernamePasswordAuthenticationToken("jefe_admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRACION")));

        assertEquals(Optional.empty(), solicitudes.empresaConSesion(sesion));
        verify(cuentas, never()).buscarPorUsuario(any());
    }

    @Test
    void unaEmpresaConSesionEsLaDeSuCuenta() {
        var sesion = new UsernamePasswordAuthenticationToken("ammu", null,
                List.of(new SimpleGrantedAuthority("ROLE_EMPRESA")));
        var acme = new Empresa(7L, CUIT, "Acme SA");
        when(cuentas.buscarPorUsuario("ammu")).thenReturn(Optional.of(new CuentaEmpresa(7L, "ammu", "hash")));
        when(repositorio.empresaPorId(7L)).thenReturn(Optional.of(acme));

        assertEquals(Optional.of(acme), solicitudes.empresaConSesion(sesion));
    }
}