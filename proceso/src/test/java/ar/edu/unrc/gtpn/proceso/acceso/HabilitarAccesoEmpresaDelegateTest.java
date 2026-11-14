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

    private static final String CLAVE = "clave-de-prueba";

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
        verify(cuentas).crear(eq(3L), eq("instituto-abc-s-a-3"), hash.capture());
        assertNotEquals(CLAVE, hash.getValue(), "la clave no se guarda en claro");
        assertTrue(encriptador.matches(CLAVE, hash.getValue()), "se guarda el BCrypt de la clave generada");
        verify(ejecucion).setVariable("accesoHabilitado", true);
    }

    @Test
    void laSegundaContratacionReutilizaLaCuentaSinCrearOtraYHabilitaElAcceso() {
        when(cuentas.buscarPorCurso(7))
                .thenReturn(Optional.of(new EmpresaDelCurso(3, "Instituto ABC S.A.", "instituto-abc-s-a-3")));

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
    void elUsuarioSaleDeLaRazonSocialSinAcentosYConElIdDeLaEmpresa() {
        assertEquals("instituto-abc-s-a-3",
                HabilitarAccesoEmpresaDelegate.usuarioDe(new EmpresaDelCurso(3, "Instituto ABC S.A.", null)));
        assertEquals("nunez-y-asociados-12",
                HabilitarAccesoEmpresaDelegate.usuarioDe(new EmpresaDelCurso(12, "Núñez y Asociados", null)));
        assertEquals("empresa-5",
                HabilitarAccesoEmpresaDelegate.usuarioDe(new EmpresaDelCurso(5, "   ", null)));
    }

    @Test
    void laClaveAleatoriaEsDistintaCadaVezYNoEsVacia() {
        String una = HabilitarAccesoEmpresaDelegate.claveAleatoria();
        String otra = HabilitarAccesoEmpresaDelegate.claveAleatoria();

        assertNotEquals(una, otra);
        assertFalse(una.isEmpty());
        assertEquals(16, una.length());
    }
}
