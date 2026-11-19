package ar.edu.unrc.gtpn.web.solicitudes;

import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

/** Formulario público para que las empresas soliciten una capacitación (enunciado §4.1.1, W-01, W-02). */
@Controller
public class SolicitudController {

    private static final String FORMULARIO = "solicitudes/formulario";

    /** Enunciado §1.1 y §3.1: con cinco o más cursos en ejecución se comunica la imposibilidad de realizar el curso. */
    static final String AVISO_NO_VIABLE = "En este momento no podemos continuar con tu solicitud: la institución alcanzó "
            + "el límite de cursos que puede atender a la vez. Podés volver a intentarlo más adelante.";

    private final SolicitudesDeCapacitacion solicitudes;

    public SolicitudController(SolicitudesDeCapacitacion solicitudes) {
        this.solicitudes = solicitudes;
    }

    @GetMapping("/solicitar")
    String formulario(@ModelAttribute("solicitud") SolicitudFormulario solicitud) {
        return FORMULARIO;
    }

    @PostMapping("/solicitar")
    String enviar(@Valid @ModelAttribute("solicitud") SolicitudFormulario solicitud, BindingResult errores,
            Model modelo, RedirectAttributes redirect) {
        if (errores.hasErrors()) {
            return FORMULARIO;
        }
        SolicitudRegistrada registrada;
        try {
            registrada = solicitudes.solicitar(solicitud);
        } catch (CuitDeOtraEmpresa error) {
            errores.rejectValue("razonSocial", "cuitDeOtraEmpresa", error.getMessage());
            return FORMULARIO;
        } catch (SolicitudNoRegistrada error) {
            modelo.addAttribute("error", error.getMessage());
            return FORMULARIO;
        }
        if (!registrada.viable()) {
            // Se vuelve al formulario con un aviso, como el resto del sitio hace con sus mensajes (docs/08 §3).
            redirect.addFlashAttribute("aviso", AVISO_NO_VIABLE);
            return "redirect:/solicitar";
        }
        return "redirect:/solicitar/enviada";
    }

    @GetMapping("/solicitar/enviada")
    String enviada() {
        return "solicitudes/enviada";
    }
}