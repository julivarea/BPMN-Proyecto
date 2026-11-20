package ar.edu.unrc.gtpn.web.tareas;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.flowable.Tarea;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesRepository;
import ar.edu.unrc.gtpn.web.seguridad.AutenticacionContraFlowable;
import ar.edu.unrc.gtpn.web.seguridad.SeguridadConfig;

@WebMvcTest(DefinirDocenteController.class)
@Import({SeguridadConfig.class, AutenticacionContraFlowable.class, PantallasDeTareas.class})
class DefinirDocenteControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ClienteFlowable flowable;

    @MockitoBean
    private BandejaDeTareas bandeja;

    @MockitoBean
    private SolicitudesRepository solicitudes;

    @Test
    void muestraLosMiembrosDelGrupoDocentes() throws Exception {
        when(flowable.tarea("t1")).thenReturn(tarea("t1", "jefe_docente"));
        when(flowable.miembrosDelGrupo("docentes")).thenReturn(List.of("jefe_docente", "docente1", "docente2"));

        mvc.perform(get("/tareas/t1/definir-docente")
                .with(user("jefe_docente").roles("DOCENTES")))
                .andExpect(status().isOk())
                .andExpect(view().name("tareas/definir-docente"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("docente1")));
    }

    @Test
    void asignaElDocenteYCompletaLaTarea() throws Exception {
        when(flowable.tarea("t1")).thenReturn(tarea("t1", "jefe_docente"));
        when(flowable.miembrosDelGrupo("docentes")).thenReturn(List.of("jefe_docente", "docente1", "docente2"));

        mvc.perform(post("/tareas/t1/definir-docente").param("docenteAsignado", "docente1")
                .with(user("jefe_docente").roles("DOCENTES")).with(csrf()))
                .andExpect(redirectedUrl("/docentes"))
                .andExpect(flash().attribute("mensaje", "Docente asignado correctamente."));

        verify(bandeja).completar("jefe_docente", "t1", Map.of("docenteAsignado", "docente1"));
    }

    @Test
    void unDocenteNoPuedeDefinirLaAsignacion() throws Exception {
        mvc.perform(get("/tareas/t1/definir-docente")
                .with(user("docente1").roles("DOCENTES")))
                .andExpect(redirectedUrl("/docentes"))
                .andExpect(flash().attribute("error",
                        "Solo el jefe docente puede definir el docente del curso."));
        verifyNoInteractions();
    }

    @Test
    void noAceptaUnUsuarioFueraDelGrupo() throws Exception {
        when(flowable.tarea("t1")).thenReturn(tarea("t1", "jefe_docente"));
        when(flowable.miembrosDelGrupo("docentes")).thenReturn(List.of("jefe_docente", "docente1", "docente2"));

        mvc.perform(post("/tareas/t1/definir-docente").param("docenteAsignado", "administrativo")
                .with(user("jefe_docente").roles("DOCENTES")).with(csrf()))
                .andExpect(redirectedUrl("/docentes"))
                .andExpect(flash().attribute("error",
                        "El docente seleccionado no pertenece al grupo de docentes."));
        verify(bandeja, never()).completar(any(), any(), anyMap());
    }

    private static Tarea tarea(String id, String asignado) {
        return new Tarea(id, "Definir docente que realizará el curso", "definirDocente", "instancia-1",
                asignado, OffsetDateTime.parse("2026-11-09T10:00:00Z"), "42");
    }

    private void verifyNoInteractions() {
        verify(flowable, never()).tarea(any());
        verify(flowable, never()).miembrosDelGrupo(any());
        verify(bandeja, never()).completar(any(), any(), anyMap());
    }
}
