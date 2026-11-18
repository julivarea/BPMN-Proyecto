package ar.edu.unrc.gtpn.web.seguridad;

import java.util.List;
import java.util.Optional;

import org.springframework.http.server.PathContainer;
import org.springframework.security.core.Authentication;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Qué rol requiere cada ruta. Es la única definición: {@link SeguridadConfig} la aplica a cada pedido y
 * {@link IngresoExitoso} la consulta para no llevar a nadie, después de ingresar, a una página que no puede ver.
 * Las reglas se evalúan en orden: gana la primera que coincide, así que va primero la más específica. Las rutas que no
 * figuran son públicas.
 */
final class ReglasDeAcceso {

    private static final String ADMINISTRACION = SeguridadConfig.ADMINISTRACION;
    private static final String DOCENTES = SeguridadConfig.DOCENTES;
    private static final String EMPRESA = SeguridadConfig.EMPRESA;

    /** Rutas y roles que pueden entrar a ellas (basta con uno). */
    record Regla(List<String> patrones, List<String> roles) {

        static Regla de(List<String> patrones, String... roles) {
            return new Regla(patrones, List.of(roles));
        }
    }

    static final List<Regla> REGLAS = List.of(
            Regla.de(List.of("/administracion/**"), ADMINISTRACION),
            Regla.de(List.of("/docentes/**"), DOCENTES),
            Regla.de(List.of("/empresas", "/empresas/**"), EMPRESA),
            // Las pantallas de informe tienen un rol esperado: docencia lo elabora y administración lo envía.
            Regla.de(List.of("/tareas/*/elaborar-informe"), DOCENTES),
            Regla.de(List.of("/tareas/*/enviar-informe"), ADMINISTRACION),
            Regla.de(List.of("/tareas/**"), ADMINISTRACION, DOCENTES));

    private ReglasDeAcceso() {
    }

    /** Si el usuario puede entrar a esa ruta (sin contexto, por ejemplo {@code /empresas/cursos/3}). */
    static boolean permite(Authentication autenticacion, String ruta) {
        Optional<Regla> regla = REGLAS.stream().filter(candidata -> coincide(candidata, ruta)).findFirst();
        if (regla.isEmpty()) {
            return true;
        }
        return regla.get().roles().stream().anyMatch(rol -> autenticacion.getAuthorities().stream()
                .anyMatch(permiso -> ("ROLE_" + rol).equals(permiso.getAuthority())));
    }

    private static boolean coincide(Regla regla, String ruta) {
        PathContainer camino = PathContainer.parsePath(ruta);
        return regla.patrones().stream()
                .anyMatch(patron -> PathPatternParser.defaultInstance.parse(patron).matches(camino));
    }
}
