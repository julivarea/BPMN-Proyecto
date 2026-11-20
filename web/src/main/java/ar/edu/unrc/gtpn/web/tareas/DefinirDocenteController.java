package ar.edu.unrc.gtpn.web.tareas;

import java.security.Principal;
import java.util.List;
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

/** Pantalla del jefe docente para elegir quién desarrollará el curso. */
@Controller
public class DefinirDocenteController {

    static final String CLAVE = "definirDocente";
    private static final String JEFE_DOCENTE = "jefe_docente";
    private static final String GRUPO_DOCENTES = "docentes";

    private final BandejaDeTareas bandeja;
    private final ClienteFlowable flowable;

    public DefinirDocenteController(BandejaDeTareas bandeja, ClienteFlowable flowable) {
        this.bandeja = bandeja;
        this.flowable = flowable;
    }

    @GetMapping("/tareas/{id}/definir-docente")
    public String mostrar(@PathVariable String id, Principal usuario, Model modelo, RedirectAttributes redirect) {
        try {
            Tarea tarea = tareaDelJefe(id, usuario);
            modelo.addAttribute("tarea", tarea);
            modelo.addAttribute("docentes", docentesDisponibles());
            modelo.addAttribute("seccion", "docentes");
            return "tareas/definir-docente";
        } catch (AccionNoPermitida error) {
            redirect.addFlashAttribute("error", error.getMessage());
            return "redirect:/docentes";
        }
    }

    @PostMapping("/tareas/{id}/definir-docente")
    public String guardar(@PathVariable String id,
            @RequestParam String docenteAsignado, Principal usuario, RedirectAttributes redirect) {
        try {
            tareaDelJefe(id, usuario);
            List<String> docentes = docentesDisponibles();
            if (!docentes.contains(docenteAsignado)) {
                throw new AccionNoPermitida("El docente seleccionado no pertenece al grupo de docentes.");
            }
            bandeja.completar(usuario.getName(), id, Map.of("docenteAsignado", docenteAsignado));
            redirect.addFlashAttribute("mensaje", "Docente asignado correctamente.");
        } catch (AccionNoPermitida error) {
            redirect.addFlashAttribute("error", error.getMessage());
        }
        return "redirect:/docentes";
    }

    private Tarea tareaDelJefe(String id, Principal usuario) {
        if (!JEFE_DOCENTE.equals(usuario.getName())) {
            throw new AccionNoPermitida("Solo el jefe docente puede definir el docente del curso.");
        }
        Tarea tarea = flowable.tarea(id);
        if (tarea == null || !CLAVE.equals(tarea.clave())) {
            throw new AccionNoPermitida("La tarea seleccionada no corresponde a definir docente.");
        }
        if (!usuario.getName().equals(tarea.asignado())) {
            throw new AccionNoPermitida("Solo el usuario asignado puede realizar esta tarea.");
        }
        return tarea;
    }

    private List<String> docentesDisponibles() {
        return flowable.miembrosDelGrupo(GRUPO_DOCENTES);
    }
}
