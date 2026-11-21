package ar.edu.unrc.gtpn.web.tareas;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ar.edu.unrc.gtpn.web.flowable.Tarea;
import ar.edu.unrc.gtpn.web.propuestas.GeneradorPdfPropuesta;
import ar.edu.unrc.gtpn.web.propuestas.Propuesta;
import ar.edu.unrc.gtpn.web.propuestas.PropuestasRepository;
import ar.edu.unrc.gtpn.web.solicitudes.Curso;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesRepository;

@Controller
public class ElaborarPropuestaController {

    /** Clave de la tarea en el modelo BPMN (docs/06 §2). */
    static final String CLAVE = "registrarYElaborarPropuesta";

    private static final String QUE_ES = "elaborar propuesta";

    private final RegistroDePropuestas registro;
    private final PantallasDeTareas pantallas;
    private final SolicitudesRepository solicitudesRepository;
    private final PropuestasRepository propuestasRepository;
    private final GeneradorPdfPropuesta generadorPdf;

    public ElaborarPropuestaController(
            RegistroDePropuestas registro,
            PantallasDeTareas pantallas,
            SolicitudesRepository solicitudesRepository,
            PropuestasRepository propuestasRepository,
            GeneradorPdfPropuesta generadorPdf) {
        this.registro = registro;
        this.pantallas = pantallas;
        this.solicitudesRepository = solicitudesRepository;
        this.propuestasRepository = propuestasRepository;
        this.generadorPdf = generadorPdf;
    }

    @GetMapping("/tareas/{id}/elaborar-propuesta")
    public String mostrarFormulario(@PathVariable String id,
                                   @RequestParam(defaultValue = "administracion") String seccion,
                                   Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = pantallas.seccion(seccion, PantallasDeTareas.ADMINISTRACION);
        try {
            Tarea tarea = pantallas.tareaDelUsuario(id, CLAVE, QUE_ES, usuario);
            Curso curso = pantallas.cursoDe(tarea);

            int siguienteVersion = propuestasRepository.ultimaVersionDe(pantallas.cursoIdDe(tarea)) + 1;

            model.addAttribute("tarea", tarea);
            model.addAttribute("curso", curso);
            model.addAttribute("version", siguienteVersion);
            model.addAttribute("seccion", sec);
            return "tareas/elaborar-propuesta";
        } catch (AccionNoPermitida e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Error al abrir la tarea: " + e.getMessage());
        }
        return "redirect:/" + sec;
    }

    @PostMapping("/tareas/{id}/elaborar-propuesta")
    public String guardarYCompletar(@PathVariable String id,
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

            Propuesta propuesta = new Propuesta(cursoId, nuevaVersion, programa, presupuesto, fechasPosibles, null);
            registro.registrarYCompletar(usuario.getName(), id, propuesta, Map.of(
                    "presupuesto", presupuesto,
                    "programa", programa,
                    "fechasPosibles", fechasPosibles
            ));

            redirect.addFlashAttribute("mensaje", "Propuesta v" + nuevaVersion + " registrada y tarea completada.");
        } catch (AccionNoPermitida e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Error al procesar la propuesta: " + e.getMessage());
        }
        return "redirect:/" + sec;
    }

    @GetMapping("/tareas/propuestas/{cursoId}/{version}/pdf")
    public ResponseEntity<byte[]> verPdf(@PathVariable Long cursoId, @PathVariable int version) {
        Propuesta propuesta = propuestasRepository.buscarPorCursoYVersion(cursoId, version)
                .orElseThrow(() -> new IllegalArgumentException("Propuesta no encontrada"));
        var curso = solicitudesRepository.buscarPorId(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado"));

        byte[] pdfBytes = generadorPdf.generarPdf(curso.nombre(), curso.razonSocialEmpresa(), propuesta);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"propuesta-v" + version + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
