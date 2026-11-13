package ar.edu.unrc.gtpn.web.tareas;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Map;
import java.util.Set;

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

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.flowable.Tarea;
import ar.edu.unrc.gtpn.web.solicitudes.GeneradorPdfPropuesta;
import ar.edu.unrc.gtpn.web.solicitudes.Propuesta;
import ar.edu.unrc.gtpn.web.solicitudes.PropuestasRepository;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesRepository;

@Controller
public class ElaborarPropuestaController {

    private static final Set<String> SECCIONES_VALIDAS = Set.of("administracion", "docentes");

    private final BandejaDeTareas bandeja;
    private final ClienteFlowable flowable;
    private final SolicitudesRepository solicitudesRepository;
    private final PropuestasRepository propuestasRepository;
    private final GeneradorPdfPropuesta generadorPdf;

    public ElaborarPropuestaController(
            BandejaDeTareas bandeja,
            ClienteFlowable flowable,
            SolicitudesRepository solicitudesRepository,
            PropuestasRepository propuestasRepository,
            GeneradorPdfPropuesta generadorPdf) {
        this.bandeja = bandeja;
        this.flowable = flowable;
        this.solicitudesRepository = solicitudesRepository;
        this.propuestasRepository = propuestasRepository;
        this.generadorPdf = generadorPdf;
    }

    private String sanitizarSeccion(String seccion) {
        return SECCIONES_VALIDAS.contains(seccion) ? seccion : "administracion";
    }

    private boolean esTareaDePropuesta(String clave) {
        return "registrarYElaborarPropuesta".equals(clave) || "elaborarPropuesta".equals(clave);
    }

    @GetMapping("/tareas/{id}/elaborar-propuesta")
    public String mostrarFormulario(@PathVariable String id,
                                   @RequestParam(defaultValue = "administracion") String seccion,
                                   Principal usuario, Model model, RedirectAttributes redirect) {
        String sec = sanitizarSeccion(seccion);
        try {
            Tarea tarea = flowable.tarea(id);

            if (!esTareaDePropuesta(tarea.clave())) {
                redirect.addFlashAttribute("error", "La tarea seleccionada no corresponde a elaborar propuesta.");
                return "redirect:/" + sec;
            }

            if (!usuario.getName().equals(tarea.asignado())) {
                redirect.addFlashAttribute("error", "Solo el usuario asignado puede realizar esta tarea.");
                return "redirect:/" + sec;
            }

            if (tarea.solicitud() == null) {
                redirect.addFlashAttribute("error", "No se pudo recuperar la solicitud vinculada a la tarea.");
                return "redirect:/" + sec;
            }

            Long cursoId = Long.parseLong(tarea.solicitud());
            var curso = solicitudesRepository.buscarPorId(cursoId)
                    .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado: " + cursoId));

            int siguienteVersion = propuestasRepository.ultimaVersionDe(cursoId) + 1;

            model.addAttribute("tarea", tarea);
            model.addAttribute("curso", curso);
            model.addAttribute("version", siguienteVersion);
            model.addAttribute("seccion", sec);
            return "tareas/elaborar-propuesta";
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Error al abrir la tarea: " + e.getMessage());
            return "redirect:/" + sec;
        }
    }

    @PostMapping("/tareas/{id}/elaborar-propuesta")
    public String guardarYCompletar(@PathVariable String id,
                                   @RequestParam(defaultValue = "administracion") String seccion,
                                   @RequestParam String programa,
                                   @RequestParam BigDecimal presupuesto,
                                   @RequestParam String fechasPosibles,
                                   Principal usuario,
                                   RedirectAttributes redirect) {
        String sec = sanitizarSeccion(seccion);
        try {
            Tarea tarea = flowable.tarea(id);

            if (!esTareaDePropuesta(tarea.clave())) {
                throw new AccionNoPermitida("La tarea seleccionada no es de elaborar propuesta.");
            }

            if (tarea.solicitud() == null) {
                throw new AccionNoPermitida("No se pudo obtener la clave del curso.");
            }

            Long cursoId = Long.parseLong(tarea.solicitud());
            int nuevaVersion = propuestasRepository.ultimaVersionDe(cursoId) + 1;

            // 1. Completar en Flowable (valida asignado y permisos antes de guardar)
            bandeja.completar(usuario.getName(), id, Map.of(
                    "presupuesto", presupuesto,
                    "programa", programa,
                    "fechasPosibles", fechasPosibles
            ));

            // 2. Persistir en BD institucional
            Propuesta propuesta = new Propuesta(cursoId, nuevaVersion, programa, presupuesto, fechasPosibles, null);
            propuestasRepository.guardar(propuesta);

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
