package ar.edu.unrc.gtpn.web;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Entrada del sitio: no hay una página de inicio propia, la raíz lleva a la pantalla de trabajo de cada usuario
 * (enunciado 4.1): la bandeja de tareas del personal, los cursos de la empresa o el formulario de solicitud si no hay
 * sesión iniciada. También es adonde vuelve el sitio después de ingresar.
 */
@Controller
public class InicioController {

    @GetMapping("/")
    String inicio(Authentication autenticacion) {
        return "redirect:" + destinoDe(autenticacion);
    }

    /** Con la sesión iniciada no tiene sentido pedir las credenciales otra vez. */
    @GetMapping("/login")
    String login(Authentication autenticacion) {
        return estaAutenticado(autenticacion) ? "redirect:/" : "login";
    }

    /** Pantalla principal según el rol. Si el usuario tiene varios, gana el primero de esta lista. */
    static String destinoDe(Authentication autenticacion) {
        if (!estaAutenticado(autenticacion)) {
            return "/solicitar";
        }
        if (tieneRol(autenticacion, "ROLE_ADMINISTRACION")) {
            return "/administracion";
        }
        if (tieneRol(autenticacion, "ROLE_DOCENTES")) {
            return "/docentes";
        }
        if (tieneRol(autenticacion, "ROLE_EMPRESA")) {
            return "/empresas";
        }
        return "/solicitar";
    }

    private static boolean estaAutenticado(Authentication autenticacion) {
        return autenticacion != null && autenticacion.isAuthenticated()
                && !(autenticacion instanceof AnonymousAuthenticationToken);
    }

    private static boolean tieneRol(Authentication autenticacion, String rol) {
        for (GrantedAuthority permiso : autenticacion.getAuthorities()) {
            if (rol.equals(permiso.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
