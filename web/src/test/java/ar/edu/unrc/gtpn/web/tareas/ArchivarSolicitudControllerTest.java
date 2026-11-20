package ar.edu.unrc.gtpn.web.tareas;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArchivarSolicitudController.class)
@Import({SeguridadConfig.class, AutenticacionContraFlowable.class, PantallasDeTareas.class})
class ArchivarSolicitudControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BandejaDeTareas bandeja;

    @MockitoBean
    private ClienteFlowable flowable;

    @MockitoBean
    private SolicitudesRepository solicitudesRepository;

    @Test
    void muestraFormularioDeArchivoSiEstaAsignado() throws Exception {
        Tarea tarea = new Tarea("t3", "Archivar solicitud", "archivarSolicitud", "proc1", "jefe_admin", OffsetDateTime.now(), "10");
        Curso curso = new Curso(10L, 1L, "Empresa SA", "Java 21", "Desc", 15, "Mayo", "Juan", "j@e.com", "123");

        when(flowable.tarea("t3")).thenReturn(tarea);
        when(solicitudesRepository.buscarPorId(10L)).thenReturn(Optional.of(curso));
        when(flowable.variables("proc1")).thenReturn(Map.of("resultado", "RECHAZADA"));

        mockMvc.perform(get("/tareas/t3/archivar-solicitud")
                        .with(user("jefe_admin").roles("ADMINISTRACION")))
                .andExpect(status().isOk());
    }

    @Test
    void elMotivoDeUnaPropuestaRechazadaSeExplicaEnLugarDeMostrarElCodigo() throws Exception {
        Tarea tarea = new Tarea("t3", "Archivar solicitud", "archivarSolicitud", "proc1", "jefe_admin", OffsetDateTime.now(), "10");
        Curso curso = new Curso(10L, 1L, "Empresa SA", "Java 21", "Desc", 15, "Mayo", "Juan", "j@e.com", "123");
        when(flowable.tarea("t3")).thenReturn(tarea);
        when(solicitudesRepository.buscarPorId(10L)).thenReturn(Optional.of(curso));
        // El proceso guarda el resultado como RECHAZADO (docs/06 §2).
        when(flowable.variables("proc1")).thenReturn(Map.of("resultado", "RECHAZADO"));

        mockMvc.perform(get("/tareas/t3/archivar-solicitud")
                        .with(user("jefe_admin").roles("ADMINISTRACION")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("rechazado la propuesta")));
    }

    @Test
    void archivaSolicitudYCompletaTarea() throws Exception {
        Tarea tarea = new Tarea("t3", "Archivar solicitud", "archivarSolicitud", "proc1", "jefe_admin", OffsetDateTime.now(), "10");
        when(flowable.tarea("t3")).thenReturn(tarea);

        mockMvc.perform(post("/tareas/t3/archivar-solicitud")
                        .with(user("jefe_admin").roles("ADMINISTRACION"))
                        .with(csrf())
                        .param("seccion", "administracion"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/administracion"));

        verify(bandeja).completar(eq("jefe_admin"), eq("t3"), any());
    }
}
