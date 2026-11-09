package ar.edu.unrc.gtpn.web.seguridad;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;

/**
 * Login del personal con su usuario y contraseña de Flowable (enunciado 3.3). La base de la institución no guarda
 * cuentas del personal. Cada grupo de Flowable se convierte en un rol: {@code administracion} → {@code ADMINISTRACION}.
 */
@Component
public class AutenticacionContraFlowable implements AuthenticationProvider {

    private final ClienteFlowable flowable;

    public AutenticacionContraFlowable(ClienteFlowable flowable) {
        this.flowable = flowable;
    }

    @Override
    public Authentication authenticate(Authentication pedido) {
        String usuario = pedido.getName();
        List<String> grupos = gruposDe(usuario, String.valueOf(pedido.getCredentials()));
        List<GrantedAuthority> roles = grupos.stream()
                .map(grupo -> new SimpleGrantedAuthority("ROLE_" + grupo.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toList());
        return UsernamePasswordAuthenticationToken.authenticated(usuario, null, roles);
    }

    @Override
    public boolean supports(Class<?> tipo) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(tipo);
    }

    private List<String> gruposDe(String usuario, String clave) {
        try {
            return flowable.gruposDelUsuario(usuario, clave)
                    .orElseThrow(() -> new BadCredentialsException("Usuario o contraseña incorrectos"));
        } catch (RestClientException error) {
            throw new AuthenticationServiceException("No se pudo conectar con Flowable", error);
        }
    }
}
