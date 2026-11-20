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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La pantalla de elaboración del informe de resultados (issue #27, soporte opcional), con dobles. */
@WebMvcTest(ElaborarInformeController.class)
@Import({SeguridadConfig.class, AutenticacionContraFlowable.class, PantallasDeTareas.class})
class ElaborarInformeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BandejaDeTareas bandeja;

    @MockitoBean
    private ClienteFlowable flowable;

    @MockitoBean
    private SolicitudesRepository solicitudesRepository;

    private void laTareaEsDelJefeDocente(Map<String, Object> variables) {
        Tarea tarea = new Tarea("t5", "Elaborar informe de resultados", ElaborarInformeController.CLAVE,
                "proc1", "jefe_docente", OffsetDateTime.now(), "10");
        Curso curso = new Curso(10L, 1L, "Empresa SA", "Java 21", "Desc", 15, "Mayo", "Juan", "j@e.com", "123");

        when(flowable.tarea("t5")).thenReturn(tarea);
        when(flowable.variables("proc1")).thenReturn(variables);
        when(solicitudesRepository.buscarPorId(10L)).thenReturn(Optional.of(curso));
    }

    private void laTareaEsDelJefeDocente() {
        laTareaEsDelJefeDocente(Map.of());
    }

    @Test
    void muestraElFormularioYLosDatosDelCursoSiLaTareaEstaAsignada() throws Exception {
        laTareaEsDelJefeDocente(Map.of("informeResultados", "Borrador del informe"));

        mockMvc.perform(get("/tareas/t5/elaborar-informe")
                .with(user("jefe_docente").roles("DOCENTES")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Empresa SA")))
                .andExpect(content().string(Matchers.containsString("Java 21")))
                .andExpect(content().string(Matchers.containsString("Borrador del informe")))
                .andExpect(content().string(Matchers.containsString("Guardar informe de resultados")));
    }

    @Test
    void rechazaElEnvioSiElInformeEstaVacio() throws Exception {
        laTareaEsDelJefeDocente();

        mockMvc.perform(post("/tareas/t5/elaborar-informe")
                .with(user("jefe_docente").roles("DOCENTES"))
                .with(csrf())
                .param("seccion", "docentes")
                .param("informeResultados", "   "))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Debe ingresar el informe de resultados.")));

        verify(bandeja, never()).completar(any(), any(), any());
    }

    @Test
    void guardaElInformeYCompletaLaTarea() throws Exception {
        laTareaEsDelJefeDocente();

        mockMvc.perform(post("/tareas/t5/elaborar-informe")
                .with(user("jefe_docente").roles("DOCENTES"))
                .with(csrf())
                .param("seccion", "docentes")
                .param("informeResultados", "Resultados finales del curso."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/docentes"));

        verify(bandeja).completar(eq("jefe_docente"), eq("t5"),
                eq(Map.of("informeResultados", "Resultados finales del curso.")));
    }

    @Test
    void unUsuarioNoAsignadoNoPuedeGuardarElInforme() throws Exception {
        laTareaEsDelJefeDocente();

        mockMvc.perform(post("/tareas/t5/elaborar-informe")
                .with(user("docente1").roles("DOCENTES"))
                .with(csrf())
                .param("seccion", "docentes")
                .param("informeResultados", "Resultados finales del curso."))
                .andExpect(redirectedUrl("/docentes"))
                .andExpect(flash().attribute("error", "Solo el usuario asignado puede realizar esta tarea."));

        verify(bandeja, never()).completar(any(), any(), any());
    }

    @Test
    void unUsuarioDeAdministracionNoEntraALaPantallaDeDocencia() throws Exception {
        mockMvc.perform(get("/tareas/t5/elaborar-informe")
                .with(user("jefe_admin").roles("ADMINISTRACION")))
                .andExpect(status().isForbidden());
    }
}
