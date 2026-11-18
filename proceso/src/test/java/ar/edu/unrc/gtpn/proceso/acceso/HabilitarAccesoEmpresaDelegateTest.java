package ar.edu.unrc.gtpn.proceso.acceso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Verifica *Habilitar acceso a la empresa* (issue #17) con un repositorio simulado: la primera contratación crea
 * la cuenta, la segunda la reutiliza, y en los dos casos queda {@code accesoHabilitado = true}.
 */
class HabilitarAccesoEmpresaDelegateTest {

    private static final String CLAVE = "clave123";

    private final CuentasDeEmpresa cuentas = mock(CuentasDeEmpresa.class);
    private final DelegateExecution ejecucion = mock(DelegateExecution.class);
    private final PasswordEncoder encriptador = new BCryptPasswordEncoder();
    private final HabilitarAccesoEmpresaDelegate delegate =
            new HabilitarAccesoEmpresaDelegate(cuentas, encriptador, () -> CLAVE);

    @BeforeEach
    void laInstanciaEsDelCurso7() {
        when(ejecucion.getProcessInstanceBusinessKey()).thenReturn("7");
        when(ejecucion.getProcessInstanceId()).thenReturn("proc1");
    }

    @Test
    void laPrimeraContratacionCreaLaCuentaConLaClaveEncriptadaYHabilitaElAcceso() {
        when(cuentas.buscarPorCurso(7))
                .thenReturn(Optional.of(new EmpresaDelCurso(3, "Instituto ABC S.A.", null)));

        delegate.execute(ejecucion);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(cuentas).crear(eq(3L), eq("Instituto ABC S.A."), hash.capture());
        assertNotEquals(CLAVE, hash.getValue(), "la clave no se guarda en claro");
        assertTrue(encriptador.matches(CLAVE, hash.getValue()), "se guarda el BCrypt de la clave de las empresas");
        verify(ejecucion).setVariable("accesoHabilitado", true);
    }

    @Test
    void laSegundaContratacionReutilizaLaCuentaSinCrearOtraYHabilitaElAcceso() {
        when(cuentas.buscarPorCurso(7))
                .thenReturn(Optional.of(new EmpresaDelCurso(3, "Instituto ABC S.A.", "Instituto ABC S.A.")));

        delegate.execute(ejecucion);

        verify(cuentas, never()).crear(anyLong(), anyString(), anyString());
        verify(ejecucion).setVariable("accesoHabilitado", true);
    }

    @Test
    void siElCursoNoExisteLaTareaFallaYNoHabilitaElAcceso() {
        when(cuentas.buscarPorCurso(7)).thenReturn(Optional.empty());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> delegate.execute(ejecucion));

        assertTrue(error.getMessage().contains("curso 7"));
        verify(cuentas, never()).crear(anyLong(), anyString(), anyString());
        verify(ejecucion, never()).setVariable(eq("accesoHabilitado"), any());
    }

    @Test
    void unaSolicitudSinCursoComoBusinessKeyFalla() {
        when(ejecucion.getProcessInstanceBusinessKey()).thenReturn("curso-de-prueba");

        assertThrows(IllegalStateException.class, () -> delegate.execute(ejecucion));

        verify(cuentas, never()).buscarPorCurso(anyLong());
    }

    @Test
    void elUsuarioEsLaRazonSocialTalCualSeEscribio() {
        assertEquals("Instituto ABC S.A.",
                HabilitarAccesoEmpresaDelegate.usuarioDe(new EmpresaDelCurso(3, "  Instituto ABC S.A. ", null), u -> false));
        assertEquals("Núñez y Asociados",
                HabilitarAccesoEmpresaDelegate.usuarioDe(new EmpresaDelCurso(12, "Núñez y Asociados", null), u -> false));
        assertEquals("empresa",
                HabilitarAccesoEmpresaDelegate.usuarioDe(new EmpresaDelCurso(5, "   ", null), u -> false));
    }

    @Test
    void siElUsuarioYaEstaTomadoSeLeAgregaElIdDeLaEmpresa() {
        assertEquals("JV Enterprises 4",
                HabilitarAccesoEmpresaDelegate.usuarioDe(new EmpresaDelCurso(4, "JV Enterprises", null),
                        "JV Enterprises"::equals));
    }

    @Test
    void unaRazonSocialMuyLargaSeRecortaAlLargoDelUsuario() {
        String larga = "A".repeat(150);

        assertEquals(100,
                HabilitarAccesoEmpresaDelegate.usuarioDe(new EmpresaDelCurso(4, larga, null), u -> false).length());
        assertEquals(100,
                HabilitarAccesoEmpresaDelegate.usuarioDe(new EmpresaDelCurso(4, larga, null), u -> true).length());
    }

    @Test
    void laCreacionConsultaSiElUsuarioEstaTomado() {
        when(cuentas.buscarPorCurso(7)).thenReturn(Optional.of(new EmpresaDelCurso(4, "JV Enterprises", null)));
        when(cuentas.existeUsuario("JV Enterprises")).thenReturn(true);

        delegate.execute(ejecucion);

        verify(cuentas).crear(eq(4L), eq("JV Enterprises 4"), anyString());
    }
}
