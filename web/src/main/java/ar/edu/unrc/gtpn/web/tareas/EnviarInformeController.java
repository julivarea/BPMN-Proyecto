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
 * Pantalla de *Enviar informe y archivar expediente* (issue #27): el jefe administrativo consulta el informe que
 * elaboró docencia y, al confirmar, completa la tarea. Después el proceso libera los recursos y termina con
 * {@code resultado = 'FINALIZADO'} (docs/06 §4).
 */
@Controller
public class EnviarInformeController {

    /** Clave de la tarea en el modelo BPMN (docs/06 §2). */
    static final String CLAVE = "enviarInformeYArchivarExpediente";

    private static final String SIN_INFORME = "Sin informe registrado";
    private static final Set<String> SECCIONES_VALIDAS = Set.of("administracion", "docentes");

    private final BandejaDeTareas bandeja;
    private final ClienteFlowable flowable;
    private final SolicitudesRepository solicitudesRepository;

    public EnviarInformeController(
            BandejaDeTareas bandeja,
            ClienteFlowable flowable,
            SolicitudesRepository solicitudesRepository) {
        this.bandeja = bandeja;
        this.flowable = flowable;
        this.solicitudesRepository = solicitudesRepository;
    }

    @GetMapping("/tareas/{id}/enviar-informe")
    public String mostrarFormulario(@PathVariable String id,
                                    @RequestParam(defaultValue = "administracion") String seccion,
                                    Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = sanitizarSeccion(seccion);
        try {
            Tarea tarea = tareaDelUsuario(id, usuario);
            Map<String, Object> variables = flowable.variables(tarea.instanciaId());

            model.addAttribute("tarea", tarea);
            model.addAttribute("curso", cursoDe(tarea));
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
        String sec = sanitizarSeccion(seccion);
        try {
            tareaDelUsuario(id, usuario);
            bandeja.completar(usuario.getName(), id, Map.of());
            redirect.addFlashAttribute("mensaje", "Expediente archivado y curso finalizado exitosamente");
        } catch (AccionNoPermitida error) {
            redirect.addFlashAttribute("error", error.getMessage());
        } catch (Exception error) {
            redirect.addFlashAttribute("error", "Error al archivar el expediente: " + error.getMessage());
        }
        return "redirect:/" + sec;
    }

    /** La tarea pendiente, después de verificar que sea la de esta pantalla y del usuario logueado. */
    private Tarea tareaDelUsuario(String id, Principal usuario) {
        Tarea tarea = flowable.tarea(id);

        if (tarea == null || !CLAVE.equals(tarea.clave())) {
            throw new AccionNoPermitida("La tarea seleccionada no corresponde a enviar el informe y archivar el expediente.");
        }

        if (!usuario.getName().equals(tarea.asignado())) {
            throw new AccionNoPermitida("Solo el usuario asignado puede realizar esta tarea.");
        }

        return tarea;
    }

    private Curso cursoDe(Tarea tarea) {
        if (tarea.solicitud() == null) {
            throw new AccionNoPermitida("No se pudo recuperar la solicitud vinculada a la tarea.");
        }
        Long cursoId = Long.parseLong(tarea.solicitud());
        return solicitudesRepository.buscarPorId(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado: " + cursoId));
    }

    /** El informe que cargó docencia, o un texto aclaratorio si el proceso todavía no lo tiene. */
    static String informeEnTexto(Object informe) {
        if (informe == null || informe.toString().isBlank()) {
            return SIN_INFORME;
        }
        return informe.toString();
    }

    private String sanitizarSeccion(String seccion) {
        return SECCIONES_VALIDAS.contains(seccion) ? seccion : "administracion";
    }
}
