package ar.edu.unrc.gtpn.web.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AutenticacionEmpresaTest {

    private final CuentaEmpresaRepository cuentas = mock(CuentaEmpresaRepository.class);
    private final AutenticacionEmpresa autenticacion = new AutenticacionEmpresa(cuentas, new BCryptPasswordEncoder());

    @Test
    void unaCuentaEmpresaValidaObtieneElRolEmpresa() {
        when(cuentas.buscarPorUsuario("empresa-1"))
                .thenReturn(Optional.of(new CuentaEmpresa(3, "empresa-1",
                        new BCryptPasswordEncoder().encode("clave123"))));

        Authentication resultado = autenticacion.authenticate(pedido("empresa-1", "clave123"));

        assertEquals("empresa-1", resultado.getName());
        assertEquals("ROLE_EMPRESA", resultado.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void unaCuentaInexistenteDejaContinuarAlProveedorDelPersonal() {
        when(cuentas.buscarPorUsuario("personal")).thenReturn(Optional.empty());

        assertNull(autenticacion.authenticate(pedido("personal", "clave123")));
    }

    @Test
    void unaClaveEmpresaIncorrectaEsRechazada() {
        when(cuentas.buscarPorUsuario("empresa-1"))
                .thenReturn(Optional.of(new CuentaEmpresa(3, "empresa-1",
                        new BCryptPasswordEncoder().encode("clave123"))));

        assertThrows(BadCredentialsException.class,
                () -> autenticacion.authenticate(pedido("empresa-1", "otra")));
    }

    private static Authentication pedido(String usuario, String clave) {
        return UsernamePasswordAuthenticationToken.unauthenticated(usuario, clave);
    }
}
