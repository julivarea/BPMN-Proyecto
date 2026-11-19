package ar.edu.unrc.gtpn.web.solicitudes;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

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

    private static final Empresa ACME = new Empresa(7L, "30123456789", "Acme SA");

    @Test
    void conLaSesionDeUnaEmpresaLaSolicitudVaASuNombreAunqueElEnvioTraigaOtroCuit() throws Exception {
        when(solicitudes.empresaConSesion(any())).thenReturn(Optional.of(ACME));
        when(solicitudes.solicitar(any(), any())).thenReturn(new SolicitudRegistrada(42L, true));

        mvc.perform(envio("20-46309379-3", "JV Enterprises").with(user("Acme SA").roles("EMPRESA")))
                .andExpect(redirectedUrl("/solicitar/enviada"));

        verify(solicitudes).solicitar(any(), eq(Optional.of(ACME)));
    }

    @Test
    void conLaSesionDeUnaEmpresaNoSeValidanElCuitNiLaRazonSocialDelEnvio() throws Exception {
        when(solicitudes.empresaConSesion(any())).thenReturn(Optional.of(ACME));
        when(solicitudes.solicitar(any(), any())).thenReturn(new SolicitudRegistrada(42L, true));

        mvc.perform(envio("", "   ").with(user("Acme SA").roles("EMPRESA")))
                .andExpect(redirectedUrl("/solicitar/enviada"));

        verify(solicitudes).solicitar(any(), eq(Optional.of(ACME)));
    }

    @Test
    void conLaSesionDeUnaEmpresaUnErrorEnOtroCampoVuelveAlFormularioConElCuitYLaRazonSocialBloqueados() throws Exception {
        when(solicitudes.empresaConSesion(any())).thenReturn(Optional.of(ACME));

        mvc.perform(envio("30-12345678-9", "Acme SA", "").with(user("Acme SA").roles("EMPRESA")))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("solicitud", "descripcion"))
                .andExpect(model().attribute("empresaConSesion", true))
                .andExpect(content().string(Matchers.containsString("readonly")));

        verify(solicitudes, never()).solicitar(any(), any());
    }

    @Test
    void sinSesionDeEmpresaElFormularioDeUnEnvioInvalidoNoBloqueaNingunCampo() throws Exception {
        mvc.perform(envio("123", "Acme SA"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("readonly"))));
    }

    @Test
    void elErrorDeUnCuitDeOtraEmpresaSeVeEnLaPagina() throws Exception {
        when(solicitudes.solicitar(any(), any())).thenThrow(new CuitDeOtraEmpresa());

        mvc.perform(envio("30-12345678-9", "Otra Empresa SA"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Ese CUIT ya est")));
    }

    @Test
    void unaSolicitudValidaSeRegistraYRedirigeALaConfirmacion() throws Exception {
        when(solicitudes.solicitar(any(), any())).thenReturn(new SolicitudRegistrada(42L, true));

        mvc.perform(envio("30-12345678-9", "Acme SA"))
                .andExpect(redirectedUrl("/solicitar/enviada"));

        verify(solicitudes).solicitar(any(), any());
    }

    @Test
    void unaSolicitudNoViableVuelveAlFormularioConUnAvisoEnLugarDeLaConfirmacion() throws Exception {
        when(solicitudes.solicitar(any(), any())).thenReturn(new SolicitudRegistrada(42L, false));

        mvc.perform(envio("30-12345678-9", "Acme SA"))
                .andExpect(redirectedUrl("/solicitar"))
                .andExpect(flash().attribute("aviso", SolicitudController.AVISO_NO_VIABLE));
    }

    @Test
    void elAvisoDeUnaSolicitudNoViableSeMuestraEnElFormulario() throws Exception {
        mvc.perform(get("/solicitar").flashAttr("aviso", SolicitudController.AVISO_NO_VIABLE))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("alcanzó el límite de cursos")));
    }

    @Test
    void conDatosInvalidosVuelveAlFormularioSinRegistrarNada() throws Exception {
        mvc.perform(envio("123", "   "))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("solicitud", "cuit", "razonSocial"));

        verify(solicitudes, never()).solicitar(any(), any());
    }

    @Test
    void unCuitDeOtraEmpresaMuestraElErrorJuntoALaRazonSocial() throws Exception {
        when(solicitudes.solicitar(any(), any())).thenThrow(new CuitDeOtraEmpresa());

        mvc.perform(envio("30-12345678-9", "Otra Empresa SA"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("solicitud", "razonSocial"));
    }

    @Test
    void siFlowableNoRespondeSeInformaParaReintentar() throws Exception {
        when(solicitudes.solicitar(any(), any())).thenThrow(new SolicitudNoRegistrada(new RuntimeException("sin respuesta")));

        mvc.perform(envio("30-12345678-9", "Acme SA"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("No pudimos registrar la solicitud")));
    }

    @Test
    void sinTokenCsrfNoSeAceptaLaSolicitudYSeVuelveAlFormularioConUnAviso() throws Exception {
        mvc.perform(post("/solicitar").param("cuit", "30-12345678-9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/solicitar?expirada"));

        verify(solicitudes, never()).solicitar(any(), any());
    }

    /** Envío del formulario con los demás campos válidos. */
    private static MockHttpServletRequestBuilder envio(String cuit, String razonSocial) {
        return envio(cuit, razonSocial, "Curso para el área contable");
    }

    private static MockHttpServletRequestBuilder envio(String cuit, String razonSocial, String descripcion) {
        return post("/solicitar").with(csrf())
                .param("cuit", cuit)
                .param("razonSocial", razonSocial)
                .param("contactoNombre", "Ana Pérez")
                .param("contactoEmail", "ana@acme.com")
                .param("nombre", "Excel avanzado")
                .param("descripcion", descripcion)
                .param("participantesEstimados", "10")
                .param("fechasPreferidas", "Marzo, por la mañana");
    }
}