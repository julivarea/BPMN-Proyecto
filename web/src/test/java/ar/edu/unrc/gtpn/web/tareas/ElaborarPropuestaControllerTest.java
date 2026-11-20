package ar.edu.unrc.gtpn.web.tareas;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
import ar.edu.unrc.gtpn.web.propuestas.GeneradorPdfPropuesta;
import ar.edu.unrc.gtpn.web.propuestas.Propuesta;
import ar.edu.unrc.gtpn.web.propuestas.PropuestasRepository;
import ar.edu.unrc.gtpn.web.seguridad.AutenticacionContraFlowable;
import ar.edu.unrc.gtpn.web.seguridad.SeguridadConfig;
import ar.edu.unrc.gtpn.web.solicitudes.Curso;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ElaborarPropuestaController.class)
@Import({SeguridadConfig.class, AutenticacionContraFlowable.class, PantallasDeTareas.class})
class ElaborarPropuestaControllerTest {

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

    @MockitoBean
    private GeneradorPdfPropuesta generadorPdf;

    @Test
    void muestraFormularioSiEstaAsignado() throws Exception {
        Tarea tarea = new Tarea("t1", "Elaborar propuesta", "registrarYElaborarPropuesta", "proc1", "jefe_admin", OffsetDateTime.now(), "10");
        Curso curso = new Curso(10L, 1L, "Empresa SA", "Java 21", "Curso de Java", 15, "Mayo", "Juan", "j@e.com", "123");

        when(flowable.tarea("t1")).thenReturn(tarea);
        when(solicitudesRepository.buscarPorId(10L)).thenReturn(Optional.of(curso));
        when(propuestasRepository.ultimaVersionDe(10L)).thenReturn(0);

        mockMvc.perform(get("/tareas/t1/elaborar-propuesta")
                        .with(user("jefe_admin").roles("ADMINISTRACION")))
                .andExpect(status().isOk());
    }

    @Test
    void muestraElEnlaceAlPdfDeLaVersionAnterior() throws Exception {
        Tarea tarea = new Tarea("t1", "Elaborar propuesta", "registrarYElaborarPropuesta", "proc1", "jefe_admin", OffsetDateTime.now(), "10");
        Curso curso = new Curso(10L, 1L, "Empresa SA", "Java 21", "Curso de Java", 15, "Mayo", "Juan", "j@e.com", "123");

        when(flowable.tarea("t1")).thenReturn(tarea);
        when(solicitudesRepository.buscarPorId(10L)).thenReturn(Optional.of(curso));
        when(propuestasRepository.ultimaVersionDe(10L)).thenReturn(1);

        mockMvc.perform(get("/tareas/t1/elaborar-propuesta")
                        .with(user("jefe_admin").roles("ADMINISTRACION")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("/tareas/propuestas/10/1/pdf")));
    }

    @Test
    void guardaPropuestaYCompletaTarea() throws Exception {
        Tarea tarea = new Tarea("t1", "Elaborar propuesta", "registrarYElaborarPropuesta", "proc1", "jefe_admin", OffsetDateTime.now(), "10");
        when(flowable.tarea("t1")).thenReturn(tarea);
        when(propuestasRepository.ultimaVersionDe(10L)).thenReturn(0);

        mockMvc.perform(post("/tareas/t1/elaborar-propuesta")
                        .with(user("jefe_admin").roles("ADMINISTRACION"))
                        .with(csrf())
                        .param("seccion", "administracion")
                        .param("programa", "Java 21 Avanzado")
                        .param("presupuesto", "150000")
                        .param("fechasPosibles", "Junio 2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/administracion"));

        verify(bandeja).completar(eq("jefe_admin"), eq("t1"), any());
        verify(propuestasRepository).guardar(any(Propuesta.class));
    }

    @Test
    void noGuardaLaPropuestaSiNoSePuedeCompletarLaTarea() throws Exception {
        Tarea tarea = new Tarea("t1", "Elaborar propuesta", "registrarYElaborarPropuesta", "proc1", "jefe_admin", OffsetDateTime.now(), "10");
        when(flowable.tarea("t1")).thenReturn(tarea);
        when(propuestasRepository.ultimaVersionDe(10L)).thenReturn(0);
        doThrow(new AccionNoPermitida("Solo el usuario asignado puede completar la tarea."))
                .when(bandeja).completar(any(), any(), any());

        mockMvc.perform(post("/tareas/t1/elaborar-propuesta")
                        .with(user("jefe_admin").roles("ADMINISTRACION"))
                        .with(csrf())
                        .param("seccion", "administracion")
                        .param("programa", "Java 21 Avanzado")
                        .param("presupuesto", "150000")
                        .param("fechasPosibles", "Junio 2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/administracion"));

        verify(propuestasRepository, never()).guardar(any());
    }

    @Test
    void descargaPdfDePropuesta() throws Exception {
        Propuesta propuesta = new Propuesta(1L, 10L, 1, "Programa", new BigDecimal("150000"), "Junio", null, null);
        Curso curso = new Curso(10L, 1L, "Empresa SA", "Java 21", "Desc", 15, "Junio", "Juan", "j@e.com", "123");

        when(propuestasRepository.buscarPorCursoYVersion(10L, 1)).thenReturn(Optional.of(propuesta));
        when(solicitudesRepository.buscarPorId(10L)).thenReturn(Optional.of(curso));
        when(generadorPdf.generarPdf(any(), any(), any())).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/tareas/propuestas/10/1/pdf")
                        .with(user("jefe_admin").roles("ADMINISTRACION")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }
}
