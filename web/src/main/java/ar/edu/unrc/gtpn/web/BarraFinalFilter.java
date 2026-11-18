package ar.edu.unrc.gtpn.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Trata {@code /solicitar/} igual que {@code /solicitar}. Spring MVC ya no reconoce las rutas con barra final, así que
 * se redirige a la dirección sin ella (permanente; 308 si no es un GET, para no perder el método ni los datos).
 * Corre antes que la seguridad, para que las reglas de acceso se apliquen sobre la dirección canónica.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BarraFinalFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String contexto = pedido.getContextPath();
        String ruta = pedido.getRequestURI().substring(contexto.length());
        if (ruta.length() > 1 && ruta.endsWith("/")) {
            // Sin barras al principio: "//sitio.com/" sería un enlace a otro sitio y no una ruta de este.
            String sinBarra = ruta.replaceAll("/+$", "").replaceAll("^/+", "");
            String destino = contexto + "/" + sinBarra + (pedido.getQueryString() == null ? "" : "?" + pedido.getQueryString());
            boolean esLectura = "GET".equals(pedido.getMethod()) || "HEAD".equals(pedido.getMethod());
            respuesta.setStatus(esLectura ? HttpServletResponse.SC_MOVED_PERMANENTLY : 308);
            respuesta.setHeader("Location", destino);
            return;
        }
        cadena.doFilter(pedido, respuesta);
    }
}
