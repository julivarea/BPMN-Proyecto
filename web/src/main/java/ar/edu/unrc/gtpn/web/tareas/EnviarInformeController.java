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
 * Pantalla de *Enviar informe y archivar expediente* (issue #27): el jefe administrativo consulta el informe que
 * elaboró docencia y, al confirmar, completa la tarea. Después el proceso libera los recursos y termina con
 * {@code resultado = 'FINALIZADO'} (docs/06 §4).
 */
@Controller
public class EnviarInformeController {

    /** Clave de la tarea en el modelo BPMN (docs/06 §2). */
    static final String CLAVE = "enviarInformeYArchivarExpediente";

    private static final String QUE_ES = "enviar el informe y archivar el expediente";
    private static final String SIN_INFORME = "Sin informe registrado";

    private final BandejaDeTareas bandeja;
    private final ClienteFlowable flowable;
    private final PantallasDeTareas pantallas;

    public EnviarInformeController(
            BandejaDeTareas bandeja,
            ClienteFlowable flowable,
            PantallasDeTareas pantallas) {
        this.bandeja = bandeja;
        this.flowable = flowable;
        this.pantallas = pantallas;
    }

    @GetMapping("/tareas/{id}/enviar-informe")
    public String mostrarFormulario(@PathVariable String id,
                                    @RequestParam(defaultValue = "administracion") String seccion,
                                    Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = pantallas.seccion(seccion, PantallasDeTareas.ADMINISTRACION);
        try {
            Tarea tarea = pantallas.tareaDelUsuario(id, CLAVE, QUE_ES, usuario);
            Map<String, Object> variables = flowable.variables(tarea.instanciaId());

            model.addAttribute("tarea", tarea);
            model.addAttribute("curso", pantallas.cursoDe(tarea));
            model.addAttribute("informeResultados", informeEnTexto(variables.get("informeResultados")));
            model.addAttribute("seccion", sec);
            return "tareas/enviar-informe";
        } catch (AccionNoPermitida error) {
            redirect.addFlashAttribute("error", error.getMessage());
        } catch (Exception error) {
            redirect.addFlashAttribute("error", "Error al abrir la tarea: " + error.getMessage());
        }
        return "redirect:/" + sec;
    }

    @PostMapping("/tareas/{id}/enviar-informe")
    public String archivarExpediente(@PathVariable String id,
                                     @RequestParam(defaultValue = "administracion") String seccion,
                                     Principal usuario, RedirectAttributes redirect) {
        String sec = pantallas.seccion(seccion, PantallasDeTareas.ADMINISTRACION);
        try {
            pantallas.tareaDelUsuario(id, CLAVE, QUE_ES, usuario);
            bandeja.completar(usuario.getName(), id, Map.of());
            redirect.addFlashAttribute("mensaje", "Expediente archivado y curso finalizado exitosamente");
        } catch (AccionNoPermitida error) {
            redirect.addFlashAttribute("error", error.getMessage());
        } catch (Exception error) {
            redirect.addFlashAttribute("error", "Error al archivar el expediente: " + error.getMessage());
        }
        return "redirect:/" + sec;
    }

    /** El informe que cargó docencia, o un texto aclaratorio si el proceso todavía no lo tiene. */
    static String informeEnTexto(Object informe) {
        if (informe == null || informe.toString().isBlank()) {
            return SIN_INFORME;
        }
        return informe.toString();
    }
}
