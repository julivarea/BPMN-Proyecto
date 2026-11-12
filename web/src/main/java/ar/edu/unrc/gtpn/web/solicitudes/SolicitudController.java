package ar.edu.unrc.gtpn.web.solicitudes;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SolicitudController {
    
    @GetMapping("/solicitar")
    String formulario() {
        return "solicitudes/formulario";
    }    
}
