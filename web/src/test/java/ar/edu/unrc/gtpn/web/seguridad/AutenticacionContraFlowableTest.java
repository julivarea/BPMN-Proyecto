package ar.edu.unrc.gtpn.web.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.client.ResourceAccessException;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;

class AutenticacionContraFlowableTest {

    private final ClienteFlowable flowable = mock(ClienteFlowable.class);
    private final AutenticacionContraFlowable autenticacion = new AutenticacionContraFlowable(flowable);

    @Test
    void conCredencialesValidasLosGruposDeFlowableSeConviertenEnRoles() {
        when(flowable.gruposDelUsuario("jefe_admin", "clave123")).thenReturn(Optional.of(List.of("administracion")));

        Authentication resultado = autenticacion.authenticate(pedido("jefe_admin", "clave123"));

        assertEquals("jefe_admin", resultado.getName());
        assertEquals(Set.of("ROLE_ADMINISTRACION"), resultado.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
    }

    @Test
    void conCredencialesIncorrectasNoIngresa() {
        when(flowable.gruposDelUsuario("jefe_admin", "otra")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> autenticacion.authenticate(pedido("jefe_admin", "otra")));
    }

    @Test
    void siFlowableNoRespondeSeInformaComoErrorDeAutenticacion() {
        when(flowable.gruposDelUsuario("jefe_admin", "clave123")).thenThrow(new ResourceAccessException("sin conexión"));

        assertThrows(AuthenticationServiceException.class,
                () -> autenticacion.authenticate(pedido("jefe_admin", "clave123")));
    }

    private static Authentication pedido(String usuario, String clave) {
        return UsernamePasswordAuthenticationToken.unauthenticated(usuario, clave);
    }
}
