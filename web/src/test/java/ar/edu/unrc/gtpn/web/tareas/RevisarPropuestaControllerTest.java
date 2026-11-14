package ar.edu.unrc.gtpn.web.tareas;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.flowable.Tarea;
import ar.edu.unrc.gtpn.web.propuestas.Propuesta;
import ar.edu.unrc.gtpn.web.propuestas.PropuestasRepository;
import ar.edu.unrc.gtpn.web.seguridad.AutenticacionContraFlowable;
import ar.edu.unrc.gtpn.web.seguridad.SeguridadConfig;
import ar.edu.unrc.gtpn.web.solicitudes.Curso;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RevisarPropuestaController.class)
@Import({ SeguridadConfig.class, AutenticacionContraFlowable.class })
class RevisarPropuestaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BandejaDeTareas bandeja;

    @MockitoBean
    private ClienteFlowable flowable;

    @MockitoBean
    private SolicitudesRepository solicitudesRepository;

    @MockitoBean
    private PropuestasRepository propuestasRepository;

    @Test
    void muestraFormularioDeRevisionSiEstaAsignado() throws Exception {
        Tarea tarea = new Tarea("t2", "Revisar propuesta", "revisarPropuesta", "proc1", "jefe_admin",
                OffsetDateTime.now(), "10");
        Curso curso = new Curso(10L, 1L, "Empresa SA", "Java 21", "Desc", 15, "Mayo", "Juan", "j@e.com", "123");
        Propuesta propuesta = new Propuesta(1L, 10L, 1, "Programa", new BigDecimal("150000"), "Mayo", null, null);

        when(flowable.tarea("t2")).thenReturn(tarea);
        when(solicitudesRepository.buscarPorId(10L)).thenReturn(Optional.of(curso));
        when(propuestasRepository.ultimaVersionDe(10L)).thenReturn(1);
        when(propuestasRepository.buscarPorCursoYVersion(10L, 1)).thenReturn(Optional.of(propuesta));

        mockMvc.perform(get("/tareas/t2/revisar-propuesta")
                .with(user("jefe_admin").roles("ADMINISTRACION")))
                .andExpect(status().isOk());
    }

    @Test
    void procesaDecisionDeAceptacionYCompletaTarea() throws Exception {
        Tarea tarea = new Tarea("t2", "Revisar propuesta", "revisarPropuesta", "proc1", "jefe_admin",
                OffsetDateTime.now(), "10");
        when(flowable.tarea("t2")).thenReturn(tarea);

        mockMvc.perform(post("/tareas/t2/revisar-propuesta")
                .with(user("jefe_admin").roles("ADMINISTRACION"))
                .with(csrf())
                .param("seccion", "administracion")
                .param("decision", "ACEPTADA")
                .param("observaciones", "Sin observaciones"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/administracion"));

        verify(bandeja).completar(eq("jefe_admin"), eq("t2"), any());
    }
}
