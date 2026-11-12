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

    @GetMapping("/solicitar")
    String formulario(@ModelAttribute("solicitud") SolicitudFormulario solicitud) {
        return "solicitudes/formulario";
    }

    @PostMapping("/solicitar")
    String enviar(@Valid @ModelAttribute("solicitud") SolicitudFormulario solicitud, BindingResult errores) {
        if (errores.hasErrors()) {
            return "solicitudes/formulario";
        }
        // Pasos 6 y 7: guardar la solicitud e iniciar la instancia.
        return "redirect:/solicitar/enviada";
    }

    @GetMapping("/solicitar/enviada")
    String enviada() {
        return "solicitudes/enviada";
    }
}