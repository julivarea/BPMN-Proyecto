package ar.edu.unrc.gtpn.web.tareas;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.flowable.Tarea;
import ar.edu.unrc.gtpn.web.seguridad.AutenticacionContraFlowable;
import ar.edu.unrc.gtpn.web.seguridad.SeguridadConfig;
import ar.edu.unrc.gtpn.web.solicitudes.Curso;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesRepository;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfeccionarListaDefinitivaController.class)
@Import({SeguridadConfig.class, AutenticacionContraFlowable.class, PantallasDeTareas.class})
class ConfeccionarListaDefinitivaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BandejaDeTareas bandeja;

    @MockitoBean
    private ClienteFlowable flowable;

    @MockitoBean
    private SolicitudesRepository solicitudesRepository;

    /** La tarea asignada al administrativo, con la nómina que envió la empresa en {@code participantes}. */
    private void laTareaEsDelAdministrativo() {
        Tarea tarea = new Tarea("t2", "Confeccionar lista definitiva", "confeccionarListaDefinitiva", "proc1",
                "jefe_admin", OffsetDateTime.now(), "10");
        Curso curso = new Curso(10L, 1L, "Empresa SA", "Java 21", "Desc", 15, "Mayo", "Juan", "j@e.com", "123");

        when(flowable.tarea("t2")).thenReturn(tarea);
        when(flowable.variables("proc1")).thenReturn(Map.of("participantes", List.of("Ana Pérez", "Beto Gómez")));
        when(solicitudesRepository.buscarPorId(10L)).thenReturn(Optional.of(curso));
    }

    @Test
    void muestraElFormularioConLaNominaRecibidaSiLaTareaEstaAsignada() throws Exception {
        laTareaEsDelAdministrativo();

        mockMvc.perform(get("/tareas/t2/confeccionar-lista-definitiva")
                .with(user("jefe_admin").roles("ADMINISTRACION")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Ana Pérez")))
                .andExpect(content().string(Matchers.containsString("Beto Gómez")))
                .andExpect(content().string(Matchers.containsString("Guardar lista definitiva")));
    }

    @Test
    void rechazaElEnvioSiLaListaEstaVacia() throws Exception {
        laTareaEsDelAdministrativo();

        mockMvc.perform(post("/tareas/t2/confeccionar-lista-definitiva")
                .with(user("jefe_admin").roles("ADMINISTRACION"))
                .with(csrf())
                .param("seccion", "administracion")
                .param("listaDefinitiva", "   "))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        Matchers.containsString("Debe ingresar al menos un participante en la lista definitiva")));

        verify(bandeja, never()).completar(any(), any(), any());
    }

    @Test
    void completaLaTareaConLaListaDefinitiva() throws Exception {
        laTareaEsDelAdministrativo();

        mockMvc.perform(post("/tareas/t2/confeccionar-lista-definitiva")
                .with(user("jefe_admin").roles("ADMINISTRACION"))
                .with(csrf())
                .param("seccion", "administracion")
                .param("listaDefinitiva", "Ana Pérez\nBeto Gómez\nCarla Ruiz"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/administracion"));

        verify(bandeja).completar(eq("jefe_admin"), eq("t2"),
                eq(Map.of("listaDefinitiva", "Ana Pérez\nBeto Gómez\nCarla Ruiz")));
    }

    @Test
    void noCompletaUnaTareaDeOtraPantallaAunqueSeaDelUsuario() throws Exception {
        Tarea otra = new Tarea("t9", "Archivar solicitud", "archivarSolicitud", "proc1", "jefe_admin",
                OffsetDateTime.now(), "10");
        when(flowable.tarea("t9")).thenReturn(otra);

        mockMvc.perform(post("/tareas/{id}/confeccionar-lista-definitiva", "t9")
                .with(user("jefe_admin").roles("ADMINISTRACION"))
                .with(csrf())
                .param("listaDefinitiva", "Ana Pérez"))
                .andExpect(redirectedUrl("/administracion"))
                .andExpect(flash().attribute("error", "La tarea seleccionada no corresponde a confeccionar la lista definitiva."));

        verify(bandeja, never()).completar(any(), any(), any());
    }

    @Test
    void noCompletaUnaTareaAsignadaAOtroUsuario() throws Exception {
        Tarea ajena = new Tarea("t8", "Confeccionar lista definitiva", "confeccionarListaDefinitiva", "proc1", "otro_admin",
                OffsetDateTime.now(), "10");
        when(flowable.tarea("t8")).thenReturn(ajena);

        mockMvc.perform(post("/tareas/{id}/confeccionar-lista-definitiva", "t8")
                .with(user("jefe_admin").roles("ADMINISTRACION"))
                .with(csrf())
                .param("listaDefinitiva", "Ana Pérez"))
                .andExpect(redirectedUrl("/administracion"))
                .andExpect(flash().attribute("error", "Solo el usuario asignado puede realizar esta tarea."));

        verify(bandeja, never()).completar(any(), any(), any());
    }
}
