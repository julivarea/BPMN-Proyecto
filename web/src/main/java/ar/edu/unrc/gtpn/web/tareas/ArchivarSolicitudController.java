package ar.edu.unrc.gtpn.web.tareas;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.flowable.Tarea;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesRepository;

@Controller
public class ArchivarSolicitudController {

    private static final Set<String> SECCIONES_VALIDAS = Set.of("administracion", "docentes");

    private final BandejaDeTareas bandeja;
    private final ClienteFlowable flowable;
    private final SolicitudesRepository solicitudesRepository;

    public ArchivarSolicitudController(
            BandejaDeTareas bandeja,
            ClienteFlowable flowable,
            SolicitudesRepository solicitudesRepository) {
        this.bandeja = bandeja;
        this.flowable = flowable;
        this.solicitudesRepository = solicitudesRepository;
    }

    private String sanitizarSeccion(String seccion) {
        return SECCIONES_VALIDAS.contains(seccion) ? seccion : "administracion";
    }

    @GetMapping("/tareas/{id}/archivar-solicitud")
    public String mostrarFormulario(@PathVariable String id,
                                   @RequestParam(defaultValue = "administracion") String seccion,
                                   Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = sanitizarSeccion(seccion);
        try {
            Tarea tarea = flowable.tarea(id);

            if (!"archivarSolicitud".equals(tarea.clave())) {
                redirect.addFlashAttribute("error", "La tarea seleccionada no corresponde a archivar solicitud.");
                return "redirect:/" + sec;
            }

            if (!usuario.getName().equals(tarea.asignado())) {
                redirect.addFlashAttribute("error", "Solo el usuario asignado puede realizar esta tarea.");
                return "redirect:/" + sec;
            }

            if (tarea.solicitud() == null) {
                redirect.addFlashAttribute("error", "No se pudo recuperar la solicitud vinculada a la tarea.");
                return "redirect:/" + sec;
            }

            Long cursoId = Long.parseLong(tarea.solicitud());
            var curso = solicitudesRepository.buscarPorId(cursoId)
                    .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado: " + cursoId));

            Map<String, Object> variables = flowable.variables(tarea.instanciaId());
            String resultado = (String) variables.getOrDefault("resultado", "RECHAZADA");

            model.addAttribute("tarea", tarea);
            model.addAttribute("curso", curso);
            model.addAttribute("resultado", resultado);
            model.addAttribute("seccion", sec);
            return "tareas/archivar-solicitud";
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Error al abrir la tarea: " + e.getMessage());
            return "redirect:/" + sec;
        }
    }

    @PostMapping("/tareas/{id}/archivar-solicitud")
    public String archivar(@PathVariable String id,
                           @RequestParam(defaultValue = "administracion") String seccion,
                           Principal usuario,
                           RedirectAttributes redirect) {
        String sec = sanitizarSeccion(seccion);
        try {
            Tarea tarea = flowable.tarea(id);

            if (!"archivarSolicitud".equals(tarea.clave())) {
                throw new AccionNoPermitida("La tarea seleccionada no es de archivar solicitud.");
            }

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
