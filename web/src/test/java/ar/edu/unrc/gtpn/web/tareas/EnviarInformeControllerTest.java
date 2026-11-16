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

/** La pantalla de envío del informe y archivo del expediente (issue #27), con dobles de Flowable y la base. */
@WebMvcTest(EnviarInformeController.class)
@Import({ SeguridadConfig.class, AutenticacionContraFlowable.class })
class EnviarInformeControllerTest {

    private static final String INFORME = "Los participantes completaron el curso con una evaluación satisfactoria.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BandejaDeTareas bandeja;

    @MockitoBean
    private ClienteFlowable flowable;

    @MockitoBean
    private SolicitudesRepository solicitudesRepository;

    private void laTareaEsDelJefeAdmin(Map<String, Object> variables) {
        Tarea tarea = new Tarea("t4", "Enviar informe y archivar expediente", EnviarInformeController.CLAVE,
                "proc1", "jefe_admin", OffsetDateTime.now(), "10");
        Curso curso = new Curso(10L, 1L, "Empresa SA", "Java 21", "Desc", 15, "Mayo", "Juan", "j@e.com", "123");

        when(flowable.tarea("t4")).thenReturn(tarea);
        when(flowable.variables("proc1")).thenReturn(variables);
        when(solicitudesRepository.buscarPorId(10L)).thenReturn(Optional.of(curso));
    }

    private void laTareaEsDelJefeAdmin() {
        laTareaEsDelJefeAdmin(Map.of("informeResultados", INFORME));
    }

    @Test
    void muestraElInformeYLosDatosDelCursoSiLaTareaEstaAsignada() throws Exception {
        laTareaEsDelJefeAdmin();

        mockMvc.perform(get("/tareas/t4/enviar-informe")
                .with(user("jefe_admin").roles("ADMINISTRACION")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Empresa SA")))
                .andExpect(content().string(Matchers.containsString("Java 21")))
                .andExpect(content().string(Matchers.containsString(INFORME)))
                .andExpect(content().string(
                        Matchers.containsString("Enviar informe a la empresa y archivar expediente")));
    }

    @Test
    void sinInformeRegistradoMuestraUnaAclaracion() throws Exception {
        laTareaEsDelJefeAdmin(Map.of());

        mockMvc.perform(get("/tareas/t4/enviar-informe")
                .with(user("jefe_admin").roles("ADMINISTRACION")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Sin informe registrado")));
    }

    @Test
    void unUsuarioNoAsignadoNoPuedeVerElFormulario() throws Exception {
        laTareaEsDelJefeAdmin();

        mockMvc.perform(get("/tareas/t4/enviar-informe")
                .with(user("administrativo").roles("ADMINISTRACION")))
                .andExpect(redirectedUrl("/administracion"))
                .andExpect(flash().attribute("error", "Solo el usuario asignado puede realizar esta tarea."));
    }

    @Test
    void noSeAceptaUnaTareaDeOtraClave() throws Exception {
        Tarea tarea = new Tarea("t4", "Archivar solicitud", "archivarSolicitud", "proc1", "jefe_admin",
                OffsetDateTime.now(), "10");
        when(flowable.tarea("t4")).thenReturn(tarea);

        mockMvc.perform(get("/tareas/t4/enviar-informe")
                .with(user("jefe_admin").roles("ADMINISTRACION")))
                .andExpect(redirectedUrl("/administracion"))
                .andExpect(flash().attribute("error",
                        "La tarea seleccionada no corresponde a enviar el informe y archivar el expediente."));
    }

    @Test
    void completaLaTareaYRedirigeAAdministracion() throws Exception {
        laTareaEsDelJefeAdmin();

        mockMvc.perform(post("/tareas/t4/enviar-informe")
                .with(user("jefe_admin").roles("ADMINISTRACION"))
                .with(csrf())
                .param("seccion", "administracion"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/administracion"))
                .andExpect(flash().attribute("mensaje",
                        "Expediente archivado y curso finalizado exitosamente"));

        verify(bandeja).completar(eq("jefe_admin"), eq("t4"), eq(Map.of()));
    }

    @Test
    void otroUsuarioNoPuedeCompletarLaTarea() throws Exception {
        laTareaEsDelJefeAdmin();

        mockMvc.perform(post("/tareas/t4/enviar-informe")
                .with(user("administrativo").roles("ADMINISTRACION"))
                .with(csrf())
                .param("seccion", "administracion"))
                .andExpect(redirectedUrl("/administracion"))
                .andExpect(flash().attribute("error", "Solo el usuario asignado puede realizar esta tarea."));

        verify(bandeja, never()).completar(any(), any(), any());
    }

    @Test
    void unUsuarioSinElRolDeAdministracionNoEntra() throws Exception {
        mockMvc.perform(get("/tareas/t4/enviar-informe")
                .with(user("docente1").roles("DOCENTES")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinIngresarPideLogin() throws Exception {
        mockMvc.perform(get("/tareas/t4/enviar-informe"))
                .andExpect(redirectedUrl("/login"));
    }
}
