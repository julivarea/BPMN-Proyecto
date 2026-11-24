package ar.edu.unrc.gtpn.web.tareas;

import java.math.BigDecimal;
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
import ar.edu.unrc.gtpn.web.propuestas.Propuesta;
import ar.edu.unrc.gtpn.web.propuestas.PropuestasRepository;
import ar.edu.unrc.gtpn.web.solicitudes.Curso;

@Controller
public class RevisarPropuestaController {

    /** Clave de la tarea en el modelo BPMN (docs/06 §2). */
    static final String CLAVE = "revisarPropuesta";

    private static final String QUE_ES = "revisar propuesta";

    private final RegistroDePropuestas registro;
    private final ClienteFlowable flowable;
    private final PantallasDeTareas pantallas;
    private final PropuestasRepository propuestasRepository;

    public RevisarPropuestaController(
            RegistroDePropuestas registro,
            ClienteFlowable flowable,
            PantallasDeTareas pantallas,
            PropuestasRepository propuestasRepository) {
        this.registro = registro;
        this.flowable = flowable;
        this.pantallas = pantallas;
        this.propuestasRepository = propuestasRepository;
    }

    @GetMapping("/tareas/{id}/revisar-propuesta")
    public String mostrarFormulario(@PathVariable String id,
                                   @RequestParam(defaultValue = "administracion") String seccion,
                                   Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = pantallas.seccion(seccion, PantallasDeTareas.ADMINISTRACION);
        try {
            Tarea tarea = pantallas.tareaDelUsuario(id, CLAVE, QUE_ES, usuario);
            Curso curso = pantallas.cursoDe(tarea);

            long cursoId = pantallas.cursoIdDe(tarea);
            int ultimaVersion = propuestasRepository.ultimaVersionDe(cursoId);
            Propuesta propuesta = propuestasRepository.buscarPorCursoYVersion(cursoId, ultimaVersion)
                    .orElseThrow(() -> new IllegalArgumentException("Propuesta no encontrada para el curso: " + cursoId));

            model.addAttribute("tarea", tarea);
            model.addAttribute("curso", curso);
            model.addAttribute("propuesta", propuesta);
            model.addAttribute("observaciones", observacionesDeLaEmpresa(tarea));
            model.addAttribute("seccion", sec);
            return "tareas/revisar-propuesta";
        } catch (AccionNoPermitida e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Error al abrir la tarea: " + e.getMessage());
        }
        return "redirect:/" + sec;
    }

    /**
     * Completa la revisión con la nueva versión de la propuesta. La decisión (aceptar, pedir modificaciones o rechazar)
     * la toma la empresa y llega por la app de comunicaciones: acá solo se corrige la propuesta según sus observaciones.
     */
    @PostMapping("/tareas/{id}/revisar-propuesta")
    public String guardarNuevaVersion(@PathVariable String id,
                                  @RequestParam(defaultValue = "administracion") String seccion,
                                  @RequestParam String programa,
                                  @RequestParam BigDecimal presupuesto,
                                  @RequestParam String fechasPosibles,
                                  Principal usuario,
                                  RedirectAttributes redirect) {
        String sec = pantallas.seccion(seccion, PantallasDeTareas.ADMINISTRACION);
        try {
            Tarea tarea = pantallas.tareaDelUsuario(id, CLAVE, QUE_ES, usuario);

            long cursoId = pantallas.cursoIdDe(tarea);
            int nuevaVersion = propuestasRepository.ultimaVersionDe(cursoId) + 1;
            String observaciones = observacionesDeLaEmpresa(tarea);

            // La nueva versión guarda las observaciones de la empresa que la motivaron.
            Propuesta propuesta = new Propuesta(cursoId, nuevaVersion, programa, presupuesto, fechasPosibles,
                    observaciones);
            registro.registrarYCompletar(usuario.getName(), id, propuesta, Map.of(
                    "programa", programa,
                    "presupuesto", presupuesto,
                    "fechasPosibles", fechasPosibles
            ));

            redirect.addFlashAttribute("mensaje", "Propuesta v" + nuevaVersion + " registrada y tarea completada.");
            redirect.addFlashAttribute("enlaceMensaje", "/tareas/propuestas/" + cursoId + "/" + nuevaVersion + "/pdf");
            redirect.addFlashAttribute("enlaceMensajeTexto", "Ver PDF de la propuesta v" + nuevaVersion);
        } catch (AccionNoPermitida e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Error al procesar la revisión: " + e.getMessage());
        }
        return "redirect:/" + sec;
    }

    /** Lo que pidió la empresa al solicitar modificaciones (variable {@code observacionesEmpresa}), o null. */
    private String observacionesDeLaEmpresa(Tarea tarea) {
        Object observaciones = flowable.variables(tarea.instanciaId()).get("observacionesEmpresa");
        return observaciones == null ? null : observaciones.toString();
    }
}
