package ar.edu.unrc.gtpn.web.solicitudes;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.seguridad.AutenticacionContraFlowable;
import ar.edu.unrc.gtpn.web.seguridad.SeguridadConfig;

/** El formulario público de solicitud desde el navegador (W-01, W-02), con un doble del servicio. */
@WebMvcTest(SolicitudController.class)
@Import({SeguridadConfig.class, AutenticacionContraFlowable.class})
class SolicitudControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SolicitudesDeCapacitacion solicitudes;

    @MockitoBean
    private ClienteFlowable flowable;

    @Test
    void elFormularioEsPublico() throws Exception {
        mvc.perform(get("/solicitar"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Solicitar capacitación")));
    }

    @Test
    void unaSolicitudValidaSeRegistraYRedirigeALaConfirmacion() throws Exception {
        mvc.perform(envio("30-12345678-9", "Acme SA"))
                .andExpect(redirectedUrl("/solicitar/enviada"));

        verify(solicitudes).solicitar(any());
    }

    @Test
    void conDatosInvalidosVuelveAlFormularioSinRegistrarNada() throws Exception {
        mvc.perform(envio("123", "   "))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("solicitud", "cuit", "razonSocial"));

        verify(solicitudes, never()).solicitar(any());
    }

    @Test
    void unCuitDeOtraEmpresaMuestraElErrorJuntoALaRazonSocial() throws Exception {
        when(solicitudes.solicitar(any())).thenThrow(new CuitDeOtraEmpresa());

        mvc.perform(envio("30-12345678-9", "Otra Empresa SA"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("solicitud", "razonSocial"));
    }

    @Test
    void siFlowableNoRespondeSeInformaParaReintentar() throws Exception {
        when(solicitudes.solicitar(any())).thenThrow(new SolicitudNoRegistrada(new RuntimeException("sin respuesta")));

        mvc.perform(envio("30-12345678-9", "Acme SA"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("No pudimos registrar la solicitud")));
    }

    @Test
    void sinTokenCsrfNoSeAceptaLaSolicitud() throws Exception {
        mvc.perform(post("/solicitar").param("cuit", "30-12345678-9"))
                .andExpect(status().isForbidden());

        verify(solicitudes, never()).solicitar(any());
    }

    /** Envío del formulario con los demás campos válidos. */
    private static MockHttpServletRequestBuilder envio(String cuit, String razonSocial) {
        return post("/solicitar").with(csrf())
                .param("cuit", cuit)
                .param("razonSocial", razonSocial)
                .param("contactoNombre", "Ana Pérez")
                .param("contactoEmail", "ana@acme.com")
                .param("nombre", "Excel avanzado")
                .param("descripcion", "Curso para el área contable")
                .param("participantesEstimados", "10")
                .param("fechasPreferidas", "Marzo, por la mañana");
    }
}