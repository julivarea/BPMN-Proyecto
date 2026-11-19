package ar.edu.unrc.gtpn.web.solicitudes;

import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.Set;

import jakarta.validation.Valid;

/** Formulario público para que las empresas soliciten una capacitación (enunciado §4.1.1, W-01, W-02). */
@Controller
public class SolicitudController {

    private static final String FORMULARIO = "solicitudes/formulario";

    /** Campos que, con la sesión de una empresa, el servidor toma de la sesión y no del formulario. */
    private static final Set<String> CAMPOS_DE_LA_EMPRESA = Set.of("cuit", "razonSocial");

    /** Enunciado §1.1 y §3.1: con cinco o más cursos en ejecución se comunica la imposibilidad de realizar el curso. */
    static final String AVISO_NO_VIABLE = "En este momento no podemos continuar con tu solicitud: la institución alcanzó "
            + "el límite de cursos que puede atender a la vez. Podés volver a intentarlo más adelante.";

    private final SolicitudesDeCapacitacion solicitudes;

    public SolicitudController(SolicitudesDeCapacitacion solicitudes) {
        this.solicitudes = solicitudes;
    }

    @GetMapping("/solicitar")
    String formulario(@ModelAttribute("solicitud") SolicitudFormulario solicitud, Authentication sesion,
            Model modelo) {
        solicitudes.empresaConSesion(sesion).ifPresent(empresa -> {
            modelo.addAttribute("solicitud", solicitud.conEmpresa(empresa.cuit(), empresa.razonSocial()));
            modelo.addAttribute("empresaConSesion", true);
        });
        return FORMULARIO;
    }

    /**
     * Registra la solicitud. Con la sesión de una empresa va siempre a su nombre: el CUIT y la razón social del
     * formulario ni se validan ni se usan, así que armar el envío a mano con los de otra empresa no sirve.
     */
    @PostMapping("/solicitar")
    String enviar(@Valid @ModelAttribute("solicitud") SolicitudFormulario solicitud, BindingResult errores,
            Authentication sesion, Model modelo, RedirectAttributes redirect) {
        Optional<Empresa> empresaConSesion = solicitudes.empresaConSesion(sesion);
        if (empresaConSesion.isPresent()) {
            // Si se vuelve al formulario, esos campos siguen bloqueados.
            modelo.addAttribute("empresaConSesion", true);
        }
        if (hayErrores(errores, empresaConSesion.isPresent())) {
            return FORMULARIO;
        }
        SolicitudRegistrada registrada;
        try {
            registrada = solicitudes.solicitar(solicitud, empresaConSesion);
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

    /** Hay errores de validación, sin contar los de los campos de la empresa si esos vienen de la sesión. */
    private static boolean hayErrores(BindingResult errores, boolean empresaConSesion) {
        return errores.hasGlobalErrors() || errores.getFieldErrors().stream()
                .anyMatch(error -> !(empresaConSesion && CAMPOS_DE_LA_EMPRESA.contains(error.getField())));
    }

    @GetMapping("/solicitar/enviada")
    String enviada() {
        return "solicitudes/enviada";
    }
}