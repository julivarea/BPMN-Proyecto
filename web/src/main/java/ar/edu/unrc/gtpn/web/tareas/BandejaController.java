package ar.edu.unrc.gtpn.web.tareas;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ar.edu.unrc.gtpn.web.flowable.Tarea;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesDeCapacitacion;

/**
 * Bandeja de tareas del personal: las asignadas al usuario y las disponibles para sus grupos (W-06). Es lo que muestran
 * las secciones de Administración y de Docentes.
 */
@Controller
public class BandejaController {

    /** Secciones que muestran la bandeja. Después de reclamar o completar se vuelve a la sección de la que se vino. */
    private static final Set<String> SECCIONES = Set.of("administracion", "docentes");

    private final BandejaDeTareas bandeja;
    private final SolicitudesDeCapacitacion solicitudes;

    public BandejaController(BandejaDeTareas bandeja, SolicitudesDeCapacitacion solicitudes) {
        this.bandeja = bandeja;
        this.solicitudes = solicitudes;
    }

    @GetMapping("/administracion")
    String administracion(Principal usuario, Model modelo) {
        return bandeja(usuario, modelo, "administracion");
    }

    @GetMapping("/docentes")
    String docentes(Principal usuario, Model modelo) {
        return bandeja(usuario, modelo, "docentes");
    }

    @PostMapping("/tareas/{id}/reclamar")
    String reclamar(@PathVariable String id, @RequestParam(required = false) String seccion, Principal usuario,
            RedirectAttributes redirect) {
        try {
            bandeja.reclamar(usuario.getName(), id);
            redirect.addFlashAttribute("mensaje", "Reclamaste la tarea: ahora está en tus tareas asignadas.");
        } catch (AccionNoPermitida error) {
            redirect.addFlashAttribute("error", error.getMessage());
        }
        return volverA(seccion);
    }

    /** Completa una tarea que no carga datos. Las que tienen formulario se completan desde su propia pantalla. */
    @PostMapping("/tareas/{id}/completar")
    String completar(@PathVariable String id, @RequestParam(required = false) String seccion, Principal usuario,
            RedirectAttributes redirect) {
        try {
            bandeja.completar(usuario.getName(), id, Map.of());
            redirect.addFlashAttribute("mensaje", "Completaste la tarea.");
        } catch (AccionNoPermitida error) {
            redirect.addFlashAttribute("error", error.getMessage());
        }
        return volverA(seccion);
    }

    private String bandeja(Principal usuario, Model modelo, String seccion) {
        List<Tarea> asignadas = bandeja.asignadas(usuario.getName());
        List<Tarea> disponibles = bandeja.disponibles(usuario.getName());
        List<String> businessKeys = Stream.concat(asignadas.stream(), disponibles.stream())
                .map(Tarea::solicitud)
                .toList();
        modelo.addAttribute("seccion", seccion);
        modelo.addAttribute("asignadas", asignadas);
        modelo.addAttribute("disponibles", disponibles);
        modelo.addAttribute("solicitudes", solicitudes.descripciones(businessKeys));
        modelo.addAttribute("bandeja", bandeja);
        return "tareas/bandeja";
    }

    private static String volverA(String seccion) {
        return SECCIONES.contains(seccion) ? "redirect:/" + seccion : "redirect:/";
    }
}
