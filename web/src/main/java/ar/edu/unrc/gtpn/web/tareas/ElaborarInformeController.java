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

/**
 * Pantalla de *Elaborar informe de resultados* (issue #27, soporte opcional): el jefe docente carga el texto en
 * {@code informeResultados}, que después consulta el área administrativa al cerrar el expediente. Sin esta pantalla la
 * tarea se podía completar desde la UI de Flowable; acá también se hace desde el sitio (W-11).
 */
@Controller
public class ElaborarInformeController {

    /** Clave de la tarea en el modelo BPMN (docs/06 §2). */
    static final String CLAVE = "elaborarInformeResultados";

    private static final String QUE_ES = "elaborar el informe de resultados";

    private final BandejaDeTareas bandeja;
    private final ClienteFlowable flowable;
    private final PantallasDeTareas pantallas;

    public ElaborarInformeController(
            BandejaDeTareas bandeja,
            ClienteFlowable flowable,
            PantallasDeTareas pantallas) {
        this.bandeja = bandeja;
        this.flowable = flowable;
        this.pantallas = pantallas;
    }

    @GetMapping("/tareas/{id}/elaborar-informe")
    public String mostrarFormulario(@PathVariable String id,
                                    @RequestParam(defaultValue = "docentes") String seccion,
                                    Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = pantallas.seccion(seccion, PantallasDeTareas.DOCENTES);
        try {
            Tarea tarea = pantallas.tareaDelUsuario(id, CLAVE, QUE_ES, usuario);
            agregarDatosDeLaTarea(tarea, sec, model);
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
        String sec = pantallas.seccion(seccion, PantallasDeTareas.DOCENTES);
        String informe = informeResultados.trim();
        try {
            Tarea tarea = pantallas.tareaDelUsuario(id, CLAVE, QUE_ES, usuario);
            if (informe.isEmpty()) {
                agregarDatosDeLaTarea(tarea, sec, model);
                model.addAttribute("informeResultados", informeResultados);
                model.addAttribute("error", "Debe ingresar el informe de resultados.");
                return "tareas/elaborar-informe";
            }

            bandeja.completar(usuario.getName(), id, Map.of("informeResultados", informe));
            redirect.addFlashAttribute("mensaje", "Informe de resultados guardado: el expediente queda para el área administrativa.");
        } catch (AccionNoPermitida error) {
            redirect.addFlashAttribute("error", error.getMessage());
        } catch (Exception error) {
            redirect.addFlashAttribute("error", "Error al guardar el informe: " + error.getMessage());
        }
        return "redirect:/" + sec;
    }

    private void agregarDatosDeLaTarea(Tarea tarea, String sec, Model model) {
        Map<String, Object> variables = flowable.variables(tarea.instanciaId());
        model.addAttribute("tarea", tarea);
        model.addAttribute("curso", pantallas.cursoDe(tarea));
        model.addAttribute("seccion", sec);
        model.addAttribute("informeResultados", variables.getOrDefault("informeResultados", ""));
    }
}
