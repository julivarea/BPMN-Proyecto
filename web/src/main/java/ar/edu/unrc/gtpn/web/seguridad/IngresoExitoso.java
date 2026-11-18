package ar.edu.unrc.gtpn.web.seguridad;

import java.io.IOException;
import java.net.URI;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Qué hace el sitio después de ingresar. Si se llegó al login porque se pidió una página protegida, vuelve a ella; pero
 * solo si el usuario que ingresó puede verla. Si ingresó otra persona (por ejemplo, el personal en una pestaña donde
 * antes se pidió una página de empresas), seguir a esa página daría un 403: se descarta y se va a la raíz, que lleva a
 * la pantalla de trabajo del usuario.
 */
class IngresoExitoso implements AuthenticationSuccessHandler {

    private final RequestCache paginaPedida = new HttpSessionRequestCache();
    private final SavedRequestAwareAuthenticationSuccessHandler volverALaPagina =
            new SavedRequestAwareAuthenticationSuccessHandler();

    IngresoExitoso() {
        volverALaPagina.setRequestCache(paginaPedida);
        volverALaPagina.setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest pedido, HttpServletResponse respuesta,
            Authentication autenticacion) throws IOException, ServletException {
        SavedRequest guardada = paginaPedida.getRequest(pedido, respuesta);
        if (guardada != null && !ReglasDeAcceso.permite(autenticacion, rutaDe(guardada, pedido))) {
            paginaPedida.removeRequest(pedido, respuesta);
        }
        volverALaPagina.onAuthenticationSuccess(pedido, respuesta, autenticacion);
    }

    /** Ruta de la página guardada, sin el contexto de la aplicación. */
    private static String rutaDe(SavedRequest guardada, HttpServletRequest pedido) {
        String ruta = URI.create(guardada.getRedirectUrl()).getPath();
        String contexto = pedido.getContextPath();
        return ruta.startsWith(contexto) ? ruta.substring(contexto.length()) : ruta;
    }
}
