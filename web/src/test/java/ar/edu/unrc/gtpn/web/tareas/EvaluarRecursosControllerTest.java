package ar.edu.unrc.gtpn.web.tareas;

import java.time.OffsetDateTime;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvaluarRecursosController.class)
@Import({ SeguridadConfig.class, AutenticacionContraFlowable.class })
class EvaluarRecursosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BandejaDeTareas bandeja;

    @MockitoBean
    private ClienteFlowable flowable;

    @MockitoBean
    private SolicitudesRepository solicitudesRepository;

    private void laTareaEsDelAdministrativo() {
        Tarea tarea = new Tarea("t2", "Evaluar recursos", "evaluarRecursos", "proc1", "jefe_admin",
                OffsetDateTime.now(), "10");
        Curso curso = new Curso(10L, 1L, "Empresa SA", "Java 21", "Desc", 15, "Mayo", "Juan", "j@e.com", "123");

        when(flowable.tarea("t2")).thenReturn(tarea);
        when(solicitudesRepository.buscarPorId(10L)).thenReturn(Optional.of(curso));
    }

    @Test
    void muestraElFormularioSiLaTareaEstaAsignada() throws Exception {
        laTareaEsDelAdministrativo();

        mockMvc.perform(get("/tareas/t2/evaluar-recursos")
                .with(user("jefe_admin").roles("ADMINISTRACION")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Evaluar recursos")))
                .andExpect(content().string(Matchers.containsString("Aula física")))
                .andExpect(content().string(Matchers.containsString("Confirmar recursos")));
    }

    @Test
    void rechazaElEnvioSiNoSeSeleccionaNingunRecurso() throws Exception {
        laTareaEsDelAdministrativo();

        mockMvc.perform(post("/tareas/t2/evaluar-recursos")
                .with(user("jefe_admin").roles("ADMINISTRACION"))
                .with(csrf())
                .param("seccion", "administracion"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Debe seleccionar al menos un recurso para el curso")));

        verify(bandeja, never()).completar(any(), any(), any());
    }

    @Test
    void completaLaTareaConLosRecursosElegidos() throws Exception {
        laTareaEsDelAdministrativo();

        mockMvc.perform(post("/tareas/t2/evaluar-recursos")
                .with(user("jefe_admin").roles("ADMINISTRACION"))
                .with(csrf())
                .param("seccion", "administracion")
                .param("requiereAula", "true")
                .param("requiereEquipamiento", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/administracion"));

        verify(bandeja).completar(eq("jefe_admin"), eq("t2"), eq(Map.of(
                "requiereAula", true,
                "requiereAulaVirtual", false,
                "requiereEquipamiento", true)));
    }
}
