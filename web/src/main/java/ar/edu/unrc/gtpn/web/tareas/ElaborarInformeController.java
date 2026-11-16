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
 * Pantalla de *Elaborar informe de resultados* (issue #27, soporte opcional): el jefe docente carga el texto en
 * {@code informeResultados}, que después consulta el área administrativa al cerrar el expediente. Sin esta pantalla la
 * tarea se podía completar desde la UI de Flowable; acá también se hace desde el sitio (W-11).
 */
@Controller
public class ElaborarInformeController {

    /** Clave de la tarea en el modelo BPMN (docs/06 §2). */
    static final String CLAVE = "elaborarInformeResultados";

    private static final Set<String> SECCIONES_VALIDAS = Set.of("administracion", "docentes");

    private final BandejaDeTareas bandeja;
    private final ClienteFlowable flowable;
    private final SolicitudesRepository solicitudesRepository;

    public ElaborarInformeController(
            BandejaDeTareas bandeja,
            ClienteFlowable flowable,
            SolicitudesRepository solicitudesRepository) {
        this.bandeja = bandeja;
        this.flowable = flowable;
        this.solicitudesRepository = solicitudesRepository;
    }

    @GetMapping("/tareas/{id}/elaborar-informe")
    public String mostrarFormulario(@PathVariable String id,
                                    @RequestParam(defaultValue = "docentes") String seccion,
                                    Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = sanitizarSeccion(seccion);
        try {
            Tarea tarea = tareaDelUsuario(id, usuario);
            Map<String, Object> variables = flowable.variables(tarea.instanciaId());

            model.addAttribute("tarea", tarea);
            model.addAttribute("curso", cursoDe(tarea));
            model.addAttribute("informeResultados", variables.getOrDefault("informeResultados", ""));
            model.addAttribute("seccion", sec);
            return "tareas/elaborar-informe";
        } catch (AccionNoPermitida error) {
            redirect.addFlashAttribute("error", error.getMessage());
        } catch (Exception error) {
            redirect.addFlashAttribute("error", "Error al abrir la tarea: " + error.getMessage());
        }
        return "redirect:/" + sec;
    }

    @PostMapping("/tareas/{id}/elaborar-informe")
    public String guardarInforme(@PathVariable String id,
                                 @RequestParam(defaultValue = "docentes") String seccion,
                                 @RequestParam(required = false, defaultValue = "") String informeResultados,
                                 Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = sanitizarSeccion(seccion);
        String informe = informeResultados.trim();
        try {
            if (informe.isEmpty()) {
                agregarDatosDeLaTarea(id, usuario, sec, model);
                model.addAttribute("informeResultados", informeResultados);
                model.addAttribute("error", "Debe ingresar el informe de resultados.");
                return "tareas/elaborar-informe";
            }

            tareaDelUsuario(id, usuario);
            bandeja.completar(usuario.getName(), id, Map.of("informeResultados", informe));
            redirect.addFlashAttribute("mensaje", "Informe de resultados guardado: el expediente queda para el área administrativa.");
        } catch (AccionNoPermitida error) {
            redirect.addFlashAttribute("error", error.getMessage());
        } catch (Exception error) {
            redirect.addFlashAttribute("error", "Error al guardar el informe: " + error.getMessage());
        }
        return "redirect:/" + sec;
    }

    /** La tarea pendiente, después de verificar que sea la de esta pantalla y del usuario logueado. */
    private Tarea tareaDelUsuario(String id, Principal usuario) {
        Tarea tarea = flowable.tarea(id);

        if (tarea == null || !CLAVE.equals(tarea.clave())) {
            throw new AccionNoPermitida("La tarea seleccionada no corresponde a elaborar el informe de resultados.");
        }

        if (!usuario.getName().equals(tarea.asignado())) {
            throw new AccionNoPermitida("Solo el usuario asignado puede realizar esta tarea.");
        }

        return tarea;
    }

    private void agregarDatosDeLaTarea(String id, Principal usuario, String sec, Model model) {
        Tarea tarea = tareaDelUsuario(id, usuario);
        Map<String, Object> variables = flowable.variables(tarea.instanciaId());
        model.addAttribute("tarea", tarea);
        model.addAttribute("curso", cursoDe(tarea));
        model.addAttribute("seccion", sec);
        model.addAttribute("informeResultados", variables.getOrDefault("informeResultados", ""));
    }

    private Curso cursoDe(Tarea tarea) {
        if (tarea.solicitud() == null) {
            throw new AccionNoPermitida("No se pudo recuperar la solicitud vinculada a la tarea.");
        }
        Long cursoId = Long.parseLong(tarea.solicitud());
        return solicitudesRepository.buscarPorId(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado: " + cursoId));
    }

    private String sanitizarSeccion(String seccion) {
        return SECCIONES_VALIDAS.contains(seccion) ? seccion : "docentes";
    }
}
