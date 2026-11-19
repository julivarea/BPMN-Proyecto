package ar.edu.unrc.gtpn.web.empresa;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.unrc.gtpn.web.flowable.Actividad;
import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.propuestas.Propuesta;
import ar.edu.unrc.gtpn.web.propuestas.PropuestasRepository;
import ar.edu.unrc.gtpn.web.seguridad.AutenticacionContraFlowable;
import ar.edu.unrc.gtpn.web.seguridad.CuentaEmpresa;
import ar.edu.unrc.gtpn.web.seguridad.CuentaEmpresaRepository;
import ar.edu.unrc.gtpn.web.seguridad.SeguridadConfig;

/** Portal de empresas (issue #29): aislamiento por empresa y estado derivado de Flowable. */
@WebMvcTest(PortalEmpresaController.class)
@Import({ SeguridadConfig.class, AutenticacionContraFlowable.class })
class PortalEmpresaControllerTest {

    private static final long EMPRESA_A = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CuentaEmpresaRepository cuentas;

    @MockitoBean
    private CursosDeLaEmpresaRepository cursos;

    @MockitoBean
    private ClienteFlowable flowable;

    @MockitoBean
    private PropuestasRepository propuestas;

    private void laEmpresaAEstaAutenticada() {
        when(cuentas.buscarPorUsuario("empresa-a"))
                .thenReturn(Optional.of(new CuentaEmpresa(EMPRESA_A, "empresa-a", "hash")));
    }

    private static CursoDeLaEmpresa curso(long id, String estado, String instanciaId) {
        return new CursoDeLaEmpresa(id, "Curso " + id, "Descripción del curso " + id, 15, "Mayo y junio",
                null, null, null, null, estado, instanciaId, LocalDateTime.of(2026, 5, 1, 9, 0));
    }

    @Test
    void unaEmpresaNoPuedeAbrirElCursoDeOtra() throws Exception {
        laEmpresaAEstaAutenticada();
        // El curso 99 existe, pero la consulta filtra por la empresa: para la empresa A no hay nada.
        when(cursos.buscar(EMPRESA_A, 99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/empresas/cursos/99").with(user("empresa-a").roles("EMPRESA")))
                .andExpect(status().isNotFound());

        verify(flowable, never()).actividadesActivas(any());
    }

    @Test
    void enPreparacionMuestraLasActividadesDeLasRamasParalelas() throws Exception {
        laEmpresaAEstaAutenticada();
        when(cursos.buscar(EMPRESA_A, 10L)).thenReturn(Optional.of(curso(10, "EN_CURSO", "instancia-10")));
        when(flowable.actividadesActivas("instancia-10")).thenReturn(List.of(
                new Actividad("prepararCurso", "Preparar curso", "subProcess"),
                new Actividad("nominaRecibida", "recibir nómina de inscripciones", "intermediateCatchEvent"),
                new Actividad("reservarAula", "Reservar aula", "serviceTask"),
                new Actividad("prepararMateriales", "Preparar materiales", "userTask")));

        mockMvc.perform(get("/empresas/cursos/10").with(user("empresa-a").roles("EMPRESA")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("En preparación")))
                .andExpect(content().string(Matchers.containsString("recibir nómina de inscripciones")))
                .andExpect(content().string(Matchers.containsString("Reservar aula")))
                .andExpect(content().string(Matchers.containsString("Preparar materiales")))
                // El subproceso contenedor no es una actividad de una rama.
                .andExpect(content().string(Matchers.not(Matchers.containsString(">Preparar curso<"))));
    }

    @Test
    void conElCursoEnMarchaElDetalleMuestraLoAcordadoAlAceptar() throws Exception {
        laEmpresaAEstaAutenticada();
        when(cursos.buscar(EMPRESA_A, 10L)).thenReturn(Optional.of(curso(10, "EN_CURSO", "instancia-10")));
        when(flowable.actividadesActivas("instancia-10"))
                .thenReturn(List.of(new Actividad("desarrollarCurso", "Desarrollar curso", "userTask")));
        when(flowable.variables("instancia-10")).thenReturn(Map.of("fechaInicioCurso", "2026-12-02T09:00:00"));
        when(propuestas.buscarPorCurso(10L)).thenReturn(List.of(
                new Propuesta(10L, 1, "Programa viejo", new BigDecimal("1000"), "Mayo", null),
                new Propuesta(10L, 2, "Programa acordado", new BigDecimal("1500.00"), "Diciembre", null)));

        mockMvc.perform(get("/empresas/cursos/10").with(user("empresa-a").roles("EMPRESA")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("02/12/2026")))
                .andExpect(content().string(Matchers.containsString("Programa acordado")))
                .andExpect(content().string(Matchers.containsString("1500.00")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Programa viejo"))));
    }

    @Test
    void conLiberarEquipamientoPendienteTrasElCierreElCursoEstaFinalizandoConSuInforme() throws Exception {
        laEmpresaAEstaAutenticada();
        when(cursos.buscar(EMPRESA_A, 10L)).thenReturn(Optional.of(curso(10, "EN_CURSO", "instancia-10")));
        when(flowable.actividadesActivas("instancia-10"))
                .thenReturn(List.of(new Actividad("liberarEquipamiento", "Liberar equipamiento", "userTask")));
        when(flowable.variables("instancia-10")).thenReturn(Map.of(
                "fechaInicioCurso", "2026-12-02T09:00:00",
                "resultado", "FINALIZADO",
                "informeResultados", "Todos aprobaron"));
        when(propuestas.buscarPorCurso(10L)).thenReturn(List.of());

        mockMvc.perform(get("/empresas/cursos/10").with(user("empresa-a").roles("EMPRESA")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Finalizando: liberando recursos")))
                .andExpect(content().string(Matchers.containsString("Todos aprobaron")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("En negociación"))));
    }

    @Test
    void mientrasLaCancelacionEsperaLasCompensacionesElCursoSeEstaCancelando() throws Exception {
        laEmpresaAEstaAutenticada();
        when(cursos.buscar(EMPRESA_A, 10L)).thenReturn(Optional.of(curso(10, "EN_CURSO", "instancia-10")));
        when(flowable.actividadesActivas("instancia-10")).thenReturn(List.of(
                new Actividad("cancelacion", "Cancelación", "subProcess"),
                new Actividad("confirmarCompensaciones", "Confirmar compensaciones", "userTask"),
                new Actividad("liberarEquipamiento", "Liberar equipamiento", "userTask")));
        when(flowable.variables("instancia-10")).thenReturn(Map.of());

        mockMvc.perform(get("/empresas/cursos/10").with(user("empresa-a").roles("EMPRESA")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Cancelándose: liberando recursos")));
    }

    @Test
    void sinFechaAcordadaElDetalleNoMuestraFechaDeInicio() throws Exception {
        laEmpresaAEstaAutenticada();
        when(cursos.buscar(EMPRESA_A, 10L)).thenReturn(Optional.of(curso(10, "EN_CURSO", "instancia-10")));
        when(flowable.actividadesActivas("instancia-10"))
                .thenReturn(List.of(new Actividad("revisarPropuesta", "Revisar propuesta", "userTask")));
        when(flowable.variables("instancia-10")).thenReturn(Map.of());

        mockMvc.perform(get("/empresas/cursos/10").with(user("empresa-a").roles("EMPRESA")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("Fecha de inicio"))));
    }

    @Test
    void elListadoMuestraLosCursosDeLaEmpresaConSuEstado() throws Exception {
        laEmpresaAEstaAutenticada();
        when(cursos.listar(EMPRESA_A)).thenReturn(List.of(curso(10, "EN_CURSO", "instancia-10")));
        when(flowable.actividadesActivas("instancia-10"))
                .thenReturn(List.of(new Actividad("desarrollarCurso", "Desarrollar curso", "userTask")));

        mockMvc.perform(get("/empresas").with(user("empresa-a").roles("EMPRESA")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Curso 10")))
                .andExpect(content().string(Matchers.containsString("En desarrollo")));
    }

    @Test
    void conLaInstanciaTerminadaValeElEstadoPersistido() throws Exception {
        laEmpresaAEstaAutenticada();
        when(cursos.buscar(EMPRESA_A, 10L)).thenReturn(Optional.of(curso(10, "FINALIZADO", "instancia-10")));
        when(flowable.actividadesActivas("instancia-10")).thenReturn(List.of());

        mockMvc.perform(get("/empresas/cursos/10").with(user("empresa-a").roles("EMPRESA")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Finalizado")));
    }
}
