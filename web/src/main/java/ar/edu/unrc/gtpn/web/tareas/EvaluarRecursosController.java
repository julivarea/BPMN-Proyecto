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

import ar.edu.unrc.gtpn.web.flowable.Tarea;

/**
 * Pantalla de *Evaluar recursos* (issue #21): el administrativo marca qué recursos necesita el curso
 * ({@code requiereAula}, {@code requiereAulaVirtual}, {@code requiereEquipamiento}) y con eso el proceso sigue a la
 * preparación. Los tres son casillas de verificación, así que la tarea no puede completarse sin ninguna marcada: el
 * modelo las usa para decidir qué ramas siguen.
 */
@Controller
public class EvaluarRecursosController {

    /** Clave de la tarea en el modelo BPMN (docs/06 §2). */
    static final String CLAVE = "evaluarRecursos";

    private static final String QUE_ES = "evaluar recursos";

    private final BandejaDeTareas bandeja;
    private final PantallasDeTareas pantallas;

    public EvaluarRecursosController(BandejaDeTareas bandeja, PantallasDeTareas pantallas) {
        this.bandeja = bandeja;
        this.pantallas = pantallas;
    }

    @GetMapping("/tareas/{id}/evaluar-recursos")
    public String mostrarFormulario(@PathVariable String id,
                                   @RequestParam(defaultValue = "administracion") String seccion,
                                   Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = pantallas.seccion(seccion, PantallasDeTareas.ADMINISTRACION);
        try {
            Tarea tarea = pantallas.tareaDelUsuario(id, CLAVE, QUE_ES, usuario);
            agregarDatosDeLaTarea(tarea, sec, model);
            return "tareas/evaluar-recursos";
        } catch (AccionNoPermitida error) {
            redirect.addFlashAttribute("error", error.getMessage());
        } catch (Exception error) {
            redirect.addFlashAttribute("error", "Error al abrir la tarea: " + error.getMessage());
        }
        return "redirect:/" + sec;
    }

    @PostMapping("/tareas/{id}/evaluar-recursos")
    public String confirmarRecursos(@PathVariable String id,
                                    @RequestParam(defaultValue = "administracion") String seccion,
                                    @RequestParam(defaultValue = "false") boolean requiereAula,
                                    @RequestParam(defaultValue = "false") boolean requiereAulaVirtual,
                                    @RequestParam(defaultValue = "false") boolean requiereEquipamiento,
                                    Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = pantallas.seccion(seccion, PantallasDeTareas.ADMINISTRACION);
        try {
            Tarea tarea = pantallas.tareaDelUsuario(id, CLAVE, QUE_ES, usuario);
            if (!requiereAula && !requiereAulaVirtual && !requiereEquipamiento) {
                // El curso necesita al menos un recurso: sin eso el modelo no tiene por dónde seguir.
                agregarDatosDeLaTarea(tarea, sec, model);
                model.addAttribute("requiereAula", requiereAula);
                model.addAttribute("requiereAulaVirtual", requiereAulaVirtual);
                model.addAttribute("requiereEquipamiento", requiereEquipamiento);
                model.addAttribute("error", "Debe seleccionar al menos un recurso para el curso.");
                return "tareas/evaluar-recursos";
            }

            bandeja.completar(usuario.getName(), id, Map.of(
                    "requiereAula", requiereAula,
                    "requiereAulaVirtual", requiereAulaVirtual,
                    "requiereEquipamiento", requiereEquipamiento));

            redirect.addFlashAttribute("mensaje", "Recursos confirmados: el curso pasa a la preparación.");
            return "redirect:/" + sec;
        } catch (AccionNoPermitida error) {
            redirect.addFlashAttribute("error", error.getMessage());
        } catch (Exception error) {
            redirect.addFlashAttribute("error", "Error al confirmar los recursos: " + error.getMessage());
        }
        return "redirect:/" + sec;
    }

    /** Carga la tarea y el curso en el modelo. */
    private void agregarDatosDeLaTarea(Tarea tarea, String sec, Model model) {
        model.addAttribute("tarea", tarea);
        model.addAttribute("curso", pantallas.cursoDe(tarea));
        model.addAttribute("seccion", sec);
    }
}
