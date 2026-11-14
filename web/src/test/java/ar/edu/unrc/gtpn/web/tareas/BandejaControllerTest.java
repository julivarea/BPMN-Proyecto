package ar.edu.unrc.gtpn.web.tareas;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

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
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesDeCapacitacion;

/** La bandeja vista desde el navegador: listas, botones, reglas y seguridad, con un doble del cliente de Flowable. */
@WebMvcTest(BandejaController.class)
@Import({SeguridadConfig.class, AutenticacionContraFlowable.class, BandejaDeTareas.class})
class BandejaControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ClienteFlowable flowable;

    @MockitoBean
    private SolicitudesDeCapacitacion solicitudes;
    
    @Test
    void laSeccionDeAdministracionMuestraLasTareasAsignadasYLasDisponibles() throws Exception {
        when(flowable.tareasAsignadas("administrativo"))
                .thenReturn(List.of(tarea("t1", "Archivar solicitud", "archivarSolicitud", "administrativo")));
        when(flowable.tareasDisponibles("administrativo"))
                .thenReturn(List.of(tarea("t2", "Preparar materiales", "prepararMateriales", null)));

        mvc.perform(get("/administracion").with(user("administrativo").roles("ADMINISTRACION")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("42")))
                .andExpect(content().string(Matchers.containsString("Archivar solicitud")))
                .andExpect(content().string(Matchers.containsString("/tareas/t1/archivar-solicitud")))
                .andExpect(content().string(Matchers.containsString("Se completa desde la app de materiales")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("/tareas/t2/reclamar"))));
    }

    @Test
    void unaTareaDeUnaSolicitudDelFormularioMuestraLaEmpresaYElCurso() throws Exception {
        when(flowable.tareasAsignadas("jefe_admin"))
                .thenReturn(List.of(tarea("t1", "Registrar y elaborar propuesta", "registrarYElaborarPropuesta", "jefe_admin")));
        when(solicitudes.descripciones(List.of("42"))).thenReturn(Map.of("42", "Acme SA · Excel avanzado"));

        mvc.perform(get("/administracion").with(user("jefe_admin").roles("ADMINISTRACION")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Acme SA · Excel avanzado")));
    }

    @Test
    void completarLaTareaDeOtroUsuarioArmandoLaUrlNoLaCompleta() throws Exception {
        when(flowable.tarea("t1")).thenReturn(tarea("t1", "Archivar solicitud", "archivarSolicitud", "jefe_admin"));

        mvc.perform(post("/tareas/t1/completar").param("seccion", "administracion")
                        .with(user("administrativo").roles("ADMINISTRACION")).with(csrf()))
                .andExpect(redirectedUrl("/administracion"))
                .andExpect(flash().attribute("error", "Solo el usuario asignado puede completar esta tarea."));
        verify(flowable, never()).completar(any(), anyMap());
    }

    @Test
    void reclamarUnaTareaDisponibleLaAsigna() throws Exception {
        when(flowable.tareasDisponibles("administrativo"))
                .thenReturn(List.of(tarea("t2", "Evaluar recursos", "evaluarRecursos", null)));

        mvc.perform(post("/tareas/t2/reclamar").param("seccion", "administracion")
                .with(user("administrativo").roles("ADMINISTRACION")).with(csrf()))
                .andExpect(redirectedUrl("/administracion"))
                .andExpect(flash().attributeExists("mensaje"));
        verify(flowable).reclamar("t2", "administrativo");
    }

    @Test
    void sinTokenCsrfNoSeAceptaElFormulario() throws Exception {
        mvc.perform(post("/tareas/t1/completar").with(user("jefe_admin").roles("ADMINISTRACION")))
                .andExpect(status().isForbidden());
        verify(flowable, never()).completar(any(), anyMap());
    }

    @Test
    void laSeccionDeDocentesMuestraLaBandejaDelDocente() throws Exception {
        when(flowable.tareasAsignadas("docente1"))
                .thenReturn(List.of(tarea("t3", "Desarrollar curso", "desarrollarCurso", "docente1")));

        mvc.perform(get("/docentes").with(user("docente1").roles("DOCENTES")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Desarrollar curso")));
    }

    @Test
    void despuesDeActuarVuelveSoloAUnaSeccionConocida() throws Exception {
        when(flowable.tarea("t1")).thenReturn(tarea("t1", "Archivar solicitud", "archivarSolicitud", "jefe_admin"));

        mvc.perform(post("/tareas/t1/completar").param("seccion", "https://otro-sitio")
                        .with(user("jefe_admin").roles("ADMINISTRACION")).with(csrf()))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void sinIngresarPideLogin() throws Exception {
        mvc.perform(get("/administracion")).andExpect(redirectedUrl("/login"));
    }

    private static final OffsetDateTime CREADA = OffsetDateTime.parse("2026-11-09T10:00:00Z");

    private static Tarea tarea(String id, String nombre, String clave, String asignado) {
        return new Tarea(id, nombre, clave, "instancia-1", asignado, CREADA, "42");
    }
}
