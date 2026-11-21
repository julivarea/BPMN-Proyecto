package ar.edu.unrc.gtpn.web.seguridad;

import java.util.List;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Autentica las cuentas creadas por el proceso de habilitación de acceso de empresas.
 */
@Component
public class AutenticacionEmpresa implements AuthenticationProvider {

    private final CuentaEmpresaRepository cuentas;
    private final PasswordEncoder passwordEncoder;

    public AutenticacionEmpresa(CuentaEmpresaRepository cuentas, PasswordEncoder passwordEncoder) {
        this.cuentas = cuentas;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication pedido) {
        String usuario = pedido.getName();
        return cuentas.buscarPorUsuario(usuario)
                .map(cuenta -> autenticar(cuenta, String.valueOf(pedido.getCredentials())))
                .orElse(null);
    }

    @Override
    public boolean supports(Class<?> tipo) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(tipo);
    }

    private Authentication autenticar(CuentaEmpresa cuenta, String clave) {
        if (!passwordEncoder.matches(clave, cuenta.passwordHash())) {
            throw new BadCredentialsException("Usuario o contraseña incorrectos");
        }
        return UsernamePasswordAuthenticationToken.authenticated(
                cuenta.usuario(), null, List.of(new SimpleGrantedAuthority("ROLE_EMPRESA")));
    }
}
