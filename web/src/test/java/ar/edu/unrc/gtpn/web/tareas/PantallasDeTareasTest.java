package ar.edu.unrc.gtpn.web.tareas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ar.edu.unrc.gtpn.web.flowable.ClienteFlowable;
import ar.edu.unrc.gtpn.web.flowable.Tarea;
import ar.edu.unrc.gtpn.web.solicitudes.Curso;
import ar.edu.unrc.gtpn.web.solicitudes.SolicitudesRepository;

/** Lo común de las pantallas de tarea: sección de retorno, verificación de la tarea y curso de la solicitud. */
class PantallasDeTareasTest {

    private final ClienteFlowable flowable = mock(ClienteFlowable.class);
    private final SolicitudesRepository solicitudes = mock(SolicitudesRepository.class);
    private final PantallasDeTareas pantallas = new PantallasDeTareas(flowable, solicitudes);
    private final Principal ana = () -> "ana";

    @Test
    void laSeccionPedidaSeRespetaSiEsUnaDelSitio() {
        assertEquals("docentes", pantallas.seccion("docentes", "administracion"));
        assertEquals("administracion", pantallas.seccion("administracion", "docentes"));
    }

    @Test
    void unaSeccionQueNoEsDelSitioVuelveALaDeLaPantalla() {
        assertEquals("administracion", pantallas.seccion("../otra", "administracion"));
        assertEquals("docentes", pantallas.seccion("", "docentes"));
        assertEquals("docentes", pantallas.seccion(null, "docentes"));
    }

    @Test
    void laTareaDeLaPantallaAsignadaAlUsuarioSeDevuelve() {
        Tarea tarea = tarea("evaluarRecursos", "ana", "42");
        when(flowable.tarea("t1")).thenReturn(tarea);

        assertSame(tarea, pantallas.tareaDelUsuario("t1", "evaluarRecursos", "evaluar recursos", ana));
    }

    @Test
    void unaTareaQueYaNoExisteSeRechazaConElMismoMensajeQueUnaOtra() {
        when(flowable.tarea("t1")).thenReturn(null);

        AccionNoPermitida error = assertThrows(AccionNoPermitida.class,
                () -> pantallas.tareaDelUsuario("t1", "evaluarRecursos", "evaluar recursos", ana));

        assertEquals("La tarea seleccionada no corresponde a evaluar recursos.", error.getMessage());
    }

    @Test
    void unaTareaDeOtraPantallaSeRechaza() {
        when(flowable.tarea("t1")).thenReturn(tarea("archivarSolicitud", "ana", "42"));

        AccionNoPermitida error = assertThrows(AccionNoPermitida.class,
                () -> pantallas.tareaDelUsuario("t1", "evaluarRecursos", "evaluar recursos", ana));

        assertEquals("La tarea seleccionada no corresponde a evaluar recursos.", error.getMessage());
    }

    @Test
    void unaTareaAsignadaAOtroUsuarioSeRechaza() {
        when(flowable.tarea("t1")).thenReturn(tarea("evaluarRecursos", "beto", "42"));

        AccionNoPermitida error = assertThrows(AccionNoPermitida.class,
                () -> pantallas.tareaDelUsuario("t1", "evaluarRecursos", "evaluar recursos", ana));

        assertEquals("Solo el usuario asignado puede realizar esta tarea.", error.getMessage());
    }

    @Test
    void unaTareaSinAsignarSeRechaza() {
        when(flowable.tarea("t1")).thenReturn(tarea("evaluarRecursos", null, "42"));

        assertThrows(AccionNoPermitida.class,
                () -> pantallas.tareaDelUsuario("t1", "evaluarRecursos", "evaluar recursos", ana));
    }

    @Test
    void elCursoIdSaleDeLaSolicitudDeLaTarea() {
        assertEquals(42L, pantallas.cursoIdDe(tarea("evaluarRecursos", "ana", "42")));
    }

    @Test
    void unaTareaSinSolicitudNoTieneCurso() {
        AccionNoPermitida error = assertThrows(AccionNoPermitida.class,
                () -> pantallas.cursoIdDe(tarea("evaluarRecursos", "ana", null)));

        assertEquals("No se pudo recuperar la solicitud vinculada a la tarea.", error.getMessage());
    }

    @Test
    void elCursoDeLaTareaSeBuscaPorSuSolicitud() {
        Curso curso = new Curso(42L, 1L, "Empresa SA", "Java 21", "Curso de Java", 15, "Mayo", "Juan", "j@e.com", "123");
        when(solicitudes.buscarPorId(42L)).thenReturn(Optional.of(curso));

        assertSame(curso, pantallas.cursoDe(tarea("evaluarRecursos", "ana", "42")));
    }

    @Test
    void siElCursoNoExisteSeAvisaCualEra() {
        when(solicitudes.buscarPorId(42L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> pantallas.cursoDe(tarea("evaluarRecursos", "ana", "42")));

        assertEquals("Curso no encontrado: 42", error.getMessage());
    }

    private static Tarea tarea(String clave, String asignado, String solicitud) {
        return new Tarea("t1", "Una tarea", clave, "instancia-1", asignado, OffsetDateTime.now(), solicitud);
    }
}
