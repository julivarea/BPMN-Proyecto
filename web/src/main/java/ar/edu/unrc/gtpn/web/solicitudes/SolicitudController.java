package ar.edu.unrc.gtpn.web.solicitudes;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.validation.Valid;

/** Formulario público para que las empresas soliciten una capacitación (enunciado §4.1.1, W-01, W-02). */
@Controller
public class SolicitudController {

    private static final String FORMULARIO = "solicitudes/formulario";

    private final SolicitudesDeCapacitacion solicitudes;

    public SolicitudController(SolicitudesDeCapacitacion solicitudes) {
        this.solicitudes = solicitudes;
    }

    @GetMapping("/solicitar")
    String formulario(@ModelAttribute("solicitud") SolicitudFormulario solicitud) {
        return FORMULARIO;
    }

    @PostMapping("/solicitar")
    String enviar(@Valid @ModelAttribute("solicitud") SolicitudFormulario solicitud, BindingResult errores) {
        if (errores.hasErrors()) {
            return FORMULARIO;
        }
        try {
            solicitudes.solicitar(solicitud);
        } catch (CuitDeOtraEmpresa error) {
            errores.rejectValue("razonSocial", "cuitDeOtraEmpresa", error.getMessage());
            return FORMULARIO;
        }
        return "redirect:/solicitar/enviada";
    }

    @GetMapping("/solicitar/enviada")
    String enviada() {
        return "solicitudes/enviada";
    }
}