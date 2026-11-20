package ar.edu.unrc.gtpn.web.tareas;

import java.security.Principal;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

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
 * Pantalla de *Confeccionar lista definitiva* (issue #22): parte de la nómina que envió la empresa
 * ({@code participantes}) y la deja editable, porque el administrativo puede ajustarla antes de fijar quiénes
 * cursan. El resultado queda en {@code listaDefinitiva}, un participante por línea, que es lo que usan las tareas
 * docentes (docs/06 §2).
 */
@Controller
public class ConfeccionarListaDefinitivaController {

    /** Clave de la tarea en el modelo BPMN (docs/06 §2). */
    static final String CLAVE = "confeccionarListaDefinitiva";

    private static final String QUE_ES = "confeccionar la lista definitiva";

    private final BandejaDeTareas bandeja;
    private final ClienteFlowable flowable;
    private final PantallasDeTareas pantallas;

    public ConfeccionarListaDefinitivaController(
            BandejaDeTareas bandeja,
            ClienteFlowable flowable,
            PantallasDeTareas pantallas) {
        this.bandeja = bandeja;
        this.flowable = flowable;
        this.pantallas = pantallas;
    }

    @GetMapping("/tareas/{id}/confeccionar-lista-definitiva")
    public String mostrarFormulario(@PathVariable String id,
                                   @RequestParam(defaultValue = "administracion") String seccion,
                                   Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = pantallas.seccion(seccion, PantallasDeTareas.ADMINISTRACION);
        try {
            Tarea tarea = pantallas.tareaDelUsuario(id, CLAVE, QUE_ES, usuario);
            agregarDatosDeLaTarea(tarea, sec, model);
            return "tareas/confeccionar-lista-definitiva";
        } catch (AccionNoPermitida error) {
            redirect.addFlashAttribute("error", error.getMessage());
        } catch (Exception error) {
            redirect.addFlashAttribute("error", "Error al abrir la tarea: " + error.getMessage());
        }
        return "redirect:/" + sec;
    }

    @PostMapping("/tareas/{id}/confeccionar-lista-definitiva")
    public String guardarListaDefinitiva(@PathVariable String id,
                                         @RequestParam(defaultValue = "administracion") String seccion,
                                         @RequestParam(required = false, defaultValue = "") String listaDefinitiva,
                                         Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = pantallas.seccion(seccion, PantallasDeTareas.ADMINISTRACION);
        String lista = listaDefinitiva.trim();
        try {
            Tarea tarea = pantallas.tareaDelUsuario(id, CLAVE, QUE_ES, usuario);
            if (lista.isEmpty()) {
                agregarDatosDeLaTarea(tarea, sec, model);
                model.addAttribute("listaDefinitiva", listaDefinitiva);
                model.addAttribute("error", "Debe ingresar al menos un participante en la lista definitiva.");
                return "tareas/confeccionar-lista-definitiva";
            }

            bandeja.completar(usuario.getName(), id, Map.of("listaDefinitiva", lista));
            redirect.addFlashAttribute("mensaje", "Lista definitiva guardada: el curso sigue con la preparación.");
            return "redirect:/" + sec;
        } catch (AccionNoPermitida error) {
            redirect.addFlashAttribute("error", error.getMessage());
        } catch (Exception error) {
            redirect.addFlashAttribute("error", "Error al guardar la lista definitiva: " + error.getMessage());
        }
        return "redirect:/" + sec;
    }

    /** Carga la tarea, el curso y la nómina recibida en el modelo. */
    private void agregarDatosDeLaTarea(Tarea tarea, String sec, Model model) {
        Map<String, Object> variables = flowable.variables(tarea.instanciaId());

        model.addAttribute("tarea", tarea);
        model.addAttribute("curso", pantallas.cursoDe(tarea));
        model.addAttribute("seccion", sec);
        model.addAttribute("listaDefinitiva", nominaEnTexto(variables.get("participantes")));
    }

    /**
     * La nómina llega como lista JSON (docs/06 §2) y la pantalla la muestra un participante por línea. Si el proceso
     * todavía no recibió la nómina, el área de texto arranca vacía.
     */
    static String nominaEnTexto(Object participantes) {
        if (participantes == null) {
            return "";
        }
        if (participantes instanceof Collection<?> nomina) {
            return nomina.stream()
                    .map(ConfeccionarListaDefinitivaController::participanteEnTexto)
                    .collect(Collectors.joining("\n"));
        }
        return participantes.toString();
    }

    private static String participanteEnTexto(Object participante) {
        if (participante instanceof Map<?, ?> datos && datos.get("nombre") != null) {
            return datos.get("nombre").toString();
        }
        return participante.toString();
    }
}
