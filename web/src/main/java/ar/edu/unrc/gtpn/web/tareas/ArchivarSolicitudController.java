package ar.edu.unrc.gtpn.web.tareas;

import java.security.Principal;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.flowable.Tarea;
import ar.edu.unrc.gtpn.web.solicitudes.Curso;

@Controller
public class ArchivarSolicitudController {

    /** Clave de la tarea en el modelo BPMN (docs/06 §2). */
    static final String CLAVE = "archivarSolicitud";

    private static final String QUE_ES = "archivar solicitud";

    private final BandejaDeTareas bandeja;
    private final ClienteFlowable flowable;
    private final PantallasDeTareas pantallas;

    public ArchivarSolicitudController(
            BandejaDeTareas bandeja,
            ClienteFlowable flowable,
            PantallasDeTareas pantallas) {
        this.bandeja = bandeja;
        this.flowable = flowable;
        this.pantallas = pantallas;
    }

    @GetMapping("/tareas/{id}/archivar-solicitud")
    public String mostrarFormulario(@PathVariable String id,
                                   @RequestParam(defaultValue = "administracion") String seccion,
                                   Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = pantallas.seccion(seccion, PantallasDeTareas.ADMINISTRACION);
        try {
            Tarea tarea = pantallas.tareaDelUsuario(id, CLAVE, QUE_ES, usuario);
            Curso curso = pantallas.cursoDe(tarea);

            Map<String, Object> variables = flowable.variables(tarea.instanciaId());
            String resultado = (String) variables.getOrDefault("resultado", "RECHAZADA");

            model.addAttribute("tarea", tarea);
            model.addAttribute("curso", curso);
            model.addAttribute("resultado", resultado);
            model.addAttribute("seccion", sec);
            return "tareas/archivar-solicitud";
        } catch (AccionNoPermitida e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Error al abrir la tarea: " + e.getMessage());
        }
        return "redirect:/" + sec;
    }

    @PostMapping("/tareas/{id}/archivar-solicitud")
    public String archivar(@PathVariable String id,
                           @RequestParam(defaultValue = "administracion") String seccion,
                           Principal usuario,
                           RedirectAttributes redirect) {
        String sec = pantallas.seccion(seccion, PantallasDeTareas.ADMINISTRACION);
        try {
            pantallas.tareaDelUsuario(id, CLAVE, QUE_ES, usuario);
            bandeja.completar(usuario.getName(), id, Map.of());
            redirect.addFlashAttribute("mensaje", "La solicitud fue archivada correctamente.");
        } catch (AccionNoPermitida e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Error al archivar la solicitud: " + e.getMessage());
        }
        return "redirect:/" + sec;
    }
}
