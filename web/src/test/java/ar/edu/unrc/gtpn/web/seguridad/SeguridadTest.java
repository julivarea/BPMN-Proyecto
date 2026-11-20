package ar.edu.unrc.gtpn.web.seguridad;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.unrc.gtpn.web.empresa.CursosDeLaEmpresaRepository;
import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.propuestas.GeneradorPdfPropuesta;
import ar.edu.unrc.gtpn.web.propuestas.PropuestasRepository;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesDeCapacitacion;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesRepository;
import ar.edu.unrc.gtpn.web.tareas.BandejaDeTareas;
import ar.edu.unrc.gtpn.web.tareas.PantallasDeTareas;

/** Reglas de acceso de las secciones del sitio, sin Flowable ni base: son dobles. */
@WebMvcTest
@Import({SeguridadConfig.class, AutenticacionContraFlowable.class, AutenticacionEmpresa.class, BandejaDeTareas.class,
        PantallasDeTareas.class})
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

    @MockitoBean
    private CursosDeLaEmpresaRepository cursosDeLaEmpresa;

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

    /** Una sesión de alguien sin ingresar que pidió esa página: Spring Security la guarda y lo manda al login. */
    private MockHttpSession sesionConPaginaPedida(String ruta) throws Exception {
        return (MockHttpSession) mvc.perform(get(ruta))
                .andExpect(redirectedUrl("/login"))
                .andReturn().getRequest().getSession();
    }

    @Test
    void lasPaginasPublicasNoPidenLogin() throws Exception {
        mvc.perform(get("/solicitar")).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void laRaizSinSesionLlevaAlFormularioDeSolicitud() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/solicitar"));
    }

    @Test
    void laRaizLlevaCadaUsuarioASuPantallaDeTrabajo() throws Exception {
        mvc.perform(get("/").with(user("jefe_admin").roles(SeguridadConfig.ADMINISTRACION)))
                .andExpect(redirectedUrl("/administracion"));
        mvc.perform(get("/").with(user("docente1").roles(SeguridadConfig.DOCENTES)))
                .andExpect(redirectedUrl("/docentes"));
        mvc.perform(get("/").with(user("empresa-1").roles(SeguridadConfig.EMPRESA)))
                .andExpect(redirectedUrl("/empresas"));
    }

    @Test
    void elLoginConLaSesionIniciadaVuelveAlInicio() throws Exception {
        mvc.perform(get("/login").with(user("jefe_admin").roles(SeguridadConfig.ADMINISTRACION)))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void unaDireccionConBarraFinalSeRedirigeALaMismaSinBarra() throws Exception {
        mvc.perform(get("/solicitar/"))
                .andExpect(status().isMovedPermanently())
                .andExpect(redirectedUrl("/solicitar"));
        mvc.perform(get("/solicitar/enviada/?a=1"))
                .andExpect(status().isMovedPermanently())
                .andExpect(redirectedUrl("/solicitar/enviada?a=1"));
        mvc.perform(post("/tareas/t1/completar/"))
                .andExpect(status().is(308))
                .andExpect(redirectedUrl("/tareas/t1/completar"));
    }

    @Test
    void laBarraFinalNoPermiteRedirigirAOtroSitio() throws Exception {
        // La URI se fija a mano: MockMvc interpretaría "//sitio-ajeno.com/" como un host.
        mvc.perform(get("/x/").with(pedido -> {
            pedido.setRequestURI("//sitio-ajeno.com/");
            return pedido;
        }))
                .andExpect(status().isMovedPermanently())
                .andExpect(redirectedUrl("/sitio-ajeno.com"));
    }

    @Test
    void laDireccionConBarraFinalNoSaltaLaSeguridad() throws Exception {
        mvc.perform(get("/administracion/"))
                .andExpect(redirectedUrl("/administracion"));
        mvc.perform(get("/administracion"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void salirVuelveAlLoginSinImportarElTokenCsrfNiElMetodo() throws Exception {
        mvc.perform(post("/logout").with(user("jefe_admin").roles(SeguridadConfig.ADMINISTRACION)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?salida"));
        mvc.perform(get("/logout").with(user("jefe_admin").roles(SeguridadConfig.ADMINISTRACION)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?salida"));
    }

    @Test
    void unFormularioConElTokenViejoSinSesionVuelveAlLoginEnLugarDeUnForbidden() throws Exception {
        mvc.perform(post("/tareas/t1/completar"))
                .andExpect(redirectedUrl("/login?expirada"));
        mvc.perform(post("/login").param("username", "jefe_admin").param("password", "clave123"))
                .andExpect(redirectedUrl("/login?expirada"));
    }

    @Test
    void laSeccionDeEmpresasPideLogin() throws Exception {
        mvc.perform(get("/empresas"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void unaEmpresaEntraAEmpresasPeroNoAlPersonal() throws Exception {
        when(cuentas.buscarPorUsuario("empresa-1"))
                .thenReturn(Optional.of(new CuentaEmpresa(7, "empresa-1", "hash")));
        when(cursosDeLaEmpresa.listar(7)).thenReturn(List.of());

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
    void despuesDeIngresarSeVuelveALaPaginaPedidaSiElUsuarioPuedeVerla() throws Exception {
        when(cuentas.buscarPorUsuario("jefe_admin")).thenReturn(Optional.empty());
        when(flowable.gruposDelUsuario("jefe_admin", "clave123")).thenReturn(Optional.of(List.of("administracion")));
        MockHttpSession sesion = sesionConPaginaPedida("/tareas/t1/evaluar-recursos");

        mvc.perform(post("/login").session(sesion).with(csrf()).param("username", "jefe_admin")
                .param("password", "clave123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/tareas/t1/evaluar-recursos?continue"));
    }

    @Test
    void siElUsuarioNoPuedeVerLaPaginaPedidaVaALaRaizEnLugarDeUnForbidden() throws Exception {
        when(cuentas.buscarPorUsuario("jefe_admin")).thenReturn(Optional.empty());
        when(flowable.gruposDelUsuario("jefe_admin", "clave123")).thenReturn(Optional.of(List.of("administracion")));
        // Se pidió una página de empresas, pero ingresa el personal.
        MockHttpSession sesion = sesionConPaginaPedida("/empresas/cursos/3");

        mvc.perform(post("/login").session(sesion).with(csrf()).param("username", "jefe_admin")
                .param("password", "clave123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void laPaginaPedidaSePermiteSegunLasReglasDeAcceso() {
        var administrativo = UsernamePasswordAuthenticationToken.authenticated("a", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRACION")));
        var docente = UsernamePasswordAuthenticationToken.authenticated("d", null,
                List.of(new SimpleGrantedAuthority("ROLE_DOCENTES")));
        var empresa = UsernamePasswordAuthenticationToken.authenticated("e", null,
                List.of(new SimpleGrantedAuthority("ROLE_EMPRESA")));

        assertTrue(ReglasDeAcceso.permite(administrativo, "/administracion"));
        assertTrue(ReglasDeAcceso.permite(administrativo, "/tareas/t1/enviar-informe"));
        assertFalse(ReglasDeAcceso.permite(administrativo, "/tareas/t1/elaborar-informe"));
        assertFalse(ReglasDeAcceso.permite(administrativo, "/empresas/cursos/3"));
        assertTrue(ReglasDeAcceso.permite(docente, "/tareas/t1/elaborar-informe"));
        assertFalse(ReglasDeAcceso.permite(docente, "/administracion"));
        assertTrue(ReglasDeAcceso.permite(empresa, "/empresas"));
        assertFalse(ReglasDeAcceso.permite(empresa, "/tareas/t1/completar"));
        assertTrue(ReglasDeAcceso.permite(empresa, "/solicitar"), "las rutas sin regla son públicas");
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
