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
import ar.edu.unrc.gtpn.web.solicitudes.Curso;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesRepository;

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

    private static final Set<String> SECCIONES_VALIDAS = Set.of("administracion", "docentes");

    private final BandejaDeTareas bandeja;
    private final ClienteFlowable flowable;
    private final SolicitudesRepository solicitudesRepository;

    public EvaluarRecursosController(
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

    @GetMapping("/tareas/{id}/evaluar-recursos")
    public String mostrarFormulario(@PathVariable String id,
                                   @RequestParam(defaultValue = "administracion") String seccion,
                                   Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = sanitizarSeccion(seccion);
        try {
            agregarDatosDeLaTarea(id, usuario, sec, model);
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
        String sec = sanitizarSeccion(seccion);
        try {
            if (!requiereAula && !requiereAulaVirtual && !requiereEquipamiento) {
                // El curso necesita al menos un recurso: sin eso el modelo no tiene por dónde seguir.
                agregarDatosDeLaTarea(id, usuario, sec, model);
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

    /** Carga la tarea y el curso en el modelo, después de verificar que sea de este usuario. */
    private void agregarDatosDeLaTarea(String id, Principal usuario, String sec, Model model) {
        Tarea tarea = flowable.tarea(id);

        if (!CLAVE.equals(tarea.clave())) {
            throw new AccionNoPermitida("La tarea seleccionada no corresponde a evaluar recursos.");
        }

        if (!usuario.getName().equals(tarea.asignado())) {
            throw new AccionNoPermitida("Solo el usuario asignado puede realizar esta tarea.");
        }

        model.addAttribute("tarea", tarea);
        model.addAttribute("curso", cursoDe(tarea));
        model.addAttribute("seccion", sec);
    }

    private Curso cursoDe(Tarea tarea) {
        if (tarea.solicitud() == null) {
            throw new AccionNoPermitida("No se pudo recuperar la solicitud vinculada a la tarea.");
        }
        Long cursoId = Long.parseLong(tarea.solicitud());
        return solicitudesRepository.buscarPorId(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado: " + cursoId));
    }
}
