package ar.edu.unrc.gtpn.web.seguridad;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.propuestas.GeneradorPdfPropuesta;
import ar.edu.unrc.gtpn.web.propuestas.PropuestasRepository;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesDeCapacitacion;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesRepository;
import ar.edu.unrc.gtpn.web.tareas.BandejaDeTareas;

/** Reglas de acceso de las secciones del sitio, sin Flowable ni base: son dobles. */
@WebMvcTest
@Import({SeguridadConfig.class, AutenticacionContraFlowable.class, AutenticacionEmpresa.class, BandejaDeTareas.class})
class SeguridadTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private ClienteFlowable flowable;

    @MockitoBean
    private CuentaEmpresaRepository cuentas;

    @MockitoBean
    private SolicitudesDeCapacitacion solicitudes;

    // El @WebMvcTest sin controller levanta todos los controllers: sus repositorios también necesitan doble.
    @MockitoBean
    private SolicitudesRepository solicitudesRepository;

    @MockitoBean
    private PropuestasRepository propuestasRepository;

    @MockitoBean
    private GeneradorPdfPropuesta generadorPdf;

    @Test
    void sinIngresarLaSeccionDeAdministracionPideLogin() throws Exception {
        mvc.perform(get("/administracion"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void unAdministrativoEntraAAdministracion() throws Exception {
        mvc.perform(get("/administracion").with(user("jefe_admin").roles(SeguridadConfig.ADMINISTRACION)))
                .andExpect(status().isOk());
    }

    @Test
    void unDocenteNoEntraAAdministracion() throws Exception {
        mvc.perform(get("/administracion").with(user("docente1").roles(SeguridadConfig.DOCENTES)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unDocenteEntraADocentes() throws Exception {
        mvc.perform(get("/docentes").with(user("docente1").roles(SeguridadConfig.DOCENTES)))
                .andExpect(status().isOk());
    }

    @Test
    void lasPaginasPublicasNoPidenLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/solicitar")).andExpect(status().isOk());
    }

    @Test
    void laSeccionDeEmpresasPideLogin() throws Exception {
        mvc.perform(get("/empresas"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void unaEmpresaEntraAEmpresasPeroNoAlPersonal() throws Exception {
        mvc.perform(get("/empresas").with(user("empresa-1").roles(SeguridadConfig.EMPRESA)))
                .andExpect(status().isOk());
        mvc.perform(get("/administracion").with(user("empresa-1").roles(SeguridadConfig.EMPRESA)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/docentes").with(user("empresa-1").roles(SeguridadConfig.EMPRESA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void elLoginConCredencialesDeFlowableValidasIngresa() throws Exception {
        when(cuentas.buscarPorUsuario("jefe_admin")).thenReturn(Optional.empty());
        when(flowable.gruposDelUsuario("jefe_admin", "clave123")).thenReturn(Optional.of(List.of("administracion")));

        mvc.perform(formLogin().user("jefe_admin").password("clave123"))
                .andExpect(authenticated().withUsername("jefe_admin").withRoles(SeguridadConfig.ADMINISTRACION));
    }

    @Test
    void elLoginConCredencialesIncorrectasNoIngresa() throws Exception {
        when(cuentas.buscarPorUsuario("jefe_admin")).thenReturn(Optional.empty());
        when(flowable.gruposDelUsuario("jefe_admin", "otra")).thenReturn(Optional.empty());

        mvc.perform(formLogin().user("jefe_admin").password("otra"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void elLoginConCredencialesDeEmpresaValidasIngresa() throws Exception {
        when(cuentas.buscarPorUsuario("empresa-1"))
                .thenReturn(Optional.of(new CuentaEmpresa(7, "empresa-1",
                        passwordEncoder.encode("password"))));

        mvc.perform(formLogin().user("empresa-1").password("password"))
                .andExpect(authenticated().withUsername("empresa-1").withRoles(SeguridadConfig.EMPRESA));
    }
}
