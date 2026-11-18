package ar.edu.unrc.gtpn.web.seguridad;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Qué secciones requieren qué rol. Los roles salen de los grupos de Flowable (ver AutenticacionContraFlowable). */
@Configuration
public class SeguridadConfig {

    static final String ADMINISTRACION = "ADMINISTRACION";
    static final String DOCENTES = "DOCENTES";
    static final String EMPRESA = "EMPRESA";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, ObjectProvider<AutenticacionEmpresa> empresa,
            AutenticacionContraFlowable flowable) throws Exception {
        empresa.ifAvailable(http::authenticationProvider);
        // Salir funciona con GET y POST y sin token CSRF: cerrar la sesión no es una acción que haya que proteger, y con
        // un token viejo (por ejemplo, después de reiniciar el servidor) daba un 403 en lugar de salir.
        RequestMatcher salir = pedido -> pedido.getRequestURI().equals(pedido.getContextPath() + "/logout");
        return http
                .authenticationProvider(flowable)
                .authorizeHttpRequests(rutas -> {
                    // Qué rol requiere cada ruta está en ReglasDeAcceso; el resto del sitio es público.
                    ReglasDeAcceso.REGLAS.forEach(regla -> rutas
                            .requestMatchers(regla.patrones().toArray(String[]::new))
                            .hasAnyRole(regla.roles().toArray(String[]::new)));
                    rutas.anyRequest().permitAll();
                })
                .csrf(csrf -> csrf.ignoringRequestMatchers(salir))
                .formLogin(login -> login.loginPage("/login").successHandler(new IngresoExitoso()).permitAll())
                .logout(salida -> salida.logoutRequestMatcher(salir).logoutSuccessUrl("/login?salida"))
                .exceptionHandling(errores -> errores.accessDeniedHandler(accesoDenegado()))
                .build();
    }

    /**
     * Un formulario enviado sin sesión (porque venció o porque se reinició el servidor) trae un token CSRF que ya no
     * vale. No es falta de permisos: se vuelve a la página con un aviso (al formulario público de solicitud, o al login
     * en cualquier otro caso). Cualquier otro rechazo sigue siendo un 403.
     */
    private static AccessDeniedHandler accesoDenegado() {
        AccessDeniedHandlerImpl predeterminado = new AccessDeniedHandlerImpl();
        return (pedido, respuesta, error) -> {
            Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
            boolean sinSesion = autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken;
            if (error instanceof CsrfException && sinSesion) {
                boolean esLaSolicitud = pedido.getRequestURI().equals(pedido.getContextPath() + "/solicitar");
                respuesta.sendRedirect(pedido.getContextPath() + (esLaSolicitud ? "/solicitar" : "/login") + "?expirada");
            } else {
                predeterminado.handle(pedido, respuesta, error);
            }
        };
    }
}
