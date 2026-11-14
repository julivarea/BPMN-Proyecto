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
import ar.edu.unrc.gtpn.web.propuestas.Propuesta;
import ar.edu.unrc.gtpn.web.propuestas.PropuestasRepository;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesRepository;

@Controller
public class RevisarPropuestaController {

    private static final Set<String> SECCIONES_VALIDAS = Set.of("administracion", "docentes");

    private final BandejaDeTareas bandeja;
    private final ClienteFlowable flowable;
    private final SolicitudesRepository solicitudesRepository;
    private final PropuestasRepository propuestasRepository;

    public RevisarPropuestaController(
            BandejaDeTareas bandeja,
            ClienteFlowable flowable,
            SolicitudesRepository solicitudesRepository,
            PropuestasRepository propuestasRepository) {
        this.bandeja = bandeja;
        this.flowable = flowable;
        this.solicitudesRepository = solicitudesRepository;
        this.propuestasRepository = propuestasRepository;
    }

    private String sanitizarSeccion(String seccion) {
        return SECCIONES_VALIDAS.contains(seccion) ? seccion : "administracion";
    }

    @GetMapping("/tareas/{id}/revisar-propuesta")
    public String mostrarFormulario(@PathVariable String id,
                                   @RequestParam(defaultValue = "administracion") String seccion,
                                   Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = sanitizarSeccion(seccion);
        try {
            Tarea tarea = flowable.tarea(id);

            if (!"revisarPropuesta".equals(tarea.clave())) {
                redirect.addFlashAttribute("error", "La tarea seleccionada no corresponde a revisar propuesta.");
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

            int ultimaVersion = propuestasRepository.ultimaVersionDe(cursoId);
            Propuesta propuesta = propuestasRepository.buscarPorCursoYVersion(cursoId, ultimaVersion)
                    .orElseThrow(() -> new IllegalArgumentException("Propuesta no encontrada para el curso: " + cursoId));

            model.addAttribute("tarea", tarea);
            model.addAttribute("curso", curso);
            model.addAttribute("propuesta", propuesta);
            model.addAttribute("seccion", sec);
            return "tareas/revisar-propuesta";
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Error al abrir la tarea: " + e.getMessage());
            return "redirect:/" + sec;
        }
    }

    @PostMapping("/tareas/{id}/revisar-propuesta")
    public String procesarDecision(@PathVariable String id,
                                  @RequestParam(defaultValue = "administracion") String seccion,
                                  @RequestParam String decision,
                                  @RequestParam(required = false, defaultValue = "") String observaciones,
                                  Principal usuario,
                                  RedirectAttributes redirect) {
        String sec = sanitizarSeccion(seccion);
        try {
            Tarea tarea = flowable.tarea(id);

            if (!"revisarPropuesta".equals(tarea.clave())) {
                throw new AccionNoPermitida("La tarea seleccionada no es de revisar propuesta.");
            }

            bandeja.completar(usuario.getName(), id, Map.of(
                    "decision", decision,
                    "observaciones", observaciones
            ));

            String mensaje = switch (decision) {
                case "ACEPTADA" -> "Propuesta aceptada con éxito.";
                case "MODIFICAR" -> "Se ha solicitado modificar la propuesta.";
                case "RECHAZADA" -> "Propuesta rechazada.";
                default -> "Tarea completada.";
            };

            redirect.addFlashAttribute("mensaje", mensaje);
        } catch (AccionNoPermitida e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Error al procesar la revisión: " + e.getMessage());
        }
        return "redirect:/" + sec;
    }
}
