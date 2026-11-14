package ar.edu.unrc.gtpn.web.tareas;

import java.security.Principal;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
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
import ar.edu.unrc.gtpn.web.solicitudes.Curso;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesRepository;

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

    private static final Set<String> SECCIONES_VALIDAS = Set.of("administracion", "docentes");

    private final BandejaDeTareas bandeja;
    private final ClienteFlowable flowable;
    private final SolicitudesRepository solicitudesRepository;

    public ConfeccionarListaDefinitivaController(
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

    @GetMapping("/tareas/{id}/confeccionar-lista-definitiva")
    public String mostrarFormulario(@PathVariable String id,
                                   @RequestParam(defaultValue = "administracion") String seccion,
                                   Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = sanitizarSeccion(seccion);
        try {
            agregarDatosDeLaTarea(id, usuario, sec, model);
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
        String sec = sanitizarSeccion(seccion);
        String lista = listaDefinitiva.trim();
        try {
            if (lista.isEmpty()) {
                agregarDatosDeLaTarea(id, usuario, sec, model);
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

    /** Carga la tarea, el curso y la nómina recibida en el modelo, después de verificar que sea de este usuario. */
    private void agregarDatosDeLaTarea(String id, Principal usuario, String sec, Model model) {
        Tarea tarea = flowable.tarea(id);

        if (!CLAVE.equals(tarea.clave())) {
            throw new AccionNoPermitida("La tarea seleccionada no corresponde a confeccionar la lista definitiva.");
        }

        if (!usuario.getName().equals(tarea.asignado())) {
            throw new AccionNoPermitida("Solo el usuario asignado puede realizar esta tarea.");
        }

        Map<String, Object> variables = flowable.variables(tarea.instanciaId());

        model.addAttribute("tarea", tarea);
        model.addAttribute("curso", cursoDe(tarea));
        model.addAttribute("seccion", sec);
        model.addAttribute("listaDefinitiva", nominaEnTexto(variables.get("participantes")));
    }

    private Curso cursoDe(Tarea tarea) {
        if (tarea.solicitud() == null) {
            throw new AccionNoPermitida("No se pudo recuperar la solicitud vinculada a la tarea.");
        }
        Long cursoId = Long.parseLong(tarea.solicitud());
        return solicitudesRepository.buscarPorId(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado: " + cursoId));
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
