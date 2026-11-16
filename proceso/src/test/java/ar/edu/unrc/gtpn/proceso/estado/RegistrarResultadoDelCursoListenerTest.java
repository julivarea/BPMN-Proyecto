package ar.edu.unrc.gtpn.proceso.estado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ar.edu.unrc.gtpn.proceso.acceso.CursoRepository;
import ar.edu.unrc.gtpn.proceso.acceso.DatosResultadoCurso;

class RegistrarResultadoDelCursoListenerTest {

    private final CursoRepository cursos = org.mockito.Mockito.mock(CursoRepository.class);
    private final RegistrarResultadoDelCursoListener listener = new RegistrarResultadoDelCursoListener(cursos);

    @Test
    void registraResultadoYDatosDisponibles() {
        DelegateExecution execution = org.mockito.Mockito.mock(DelegateExecution.class);
        when(execution.getProcessInstanceBusinessKey()).thenReturn("42");
        when(execution.getVariable("resultado")).thenReturn("FINALIZADO");
        when(execution.getVariable("programa")).thenReturn("Java avanzado");
        when(execution.getVariable("presupuesto")).thenReturn(125000L);
        when(execution.getVariable("fechaInicioCurso")).thenReturn("2026-12-01T09:00:00");
        when(execution.getVariable("informeResultados")).thenReturn("Curso aprobado");

        listener.notify(execution);

        ArgumentCaptor<DatosResultadoCurso> datos = ArgumentCaptor.forClass(DatosResultadoCurso.class);
        verify(cursos).registrarResultado(org.mockito.ArgumentMatchers.eq("42"), datos.capture());
        assertEquals("FINALIZADO", datos.getValue().estado());
        assertEquals("Java avanzado", datos.getValue().programa());
        assertEquals(new BigDecimal("125000"), datos.getValue().presupuesto());
        assertEquals(LocalDateTime.of(2026, 12, 1, 9, 0), datos.getValue().fechaInicio());
        assertEquals("Curso aprobado", datos.getValue().informeResultados());
    }

    @Test
    void permiteDatosOpcionalesAusentes() {
        DelegateExecution execution = org.mockito.Mockito.mock(DelegateExecution.class);
        when(execution.getProcessInstanceBusinessKey()).thenReturn("7");
        when(execution.getVariable("resultado")).thenReturn("RECHAZADO");

        listener.notify(execution);

        verify(cursos).registrarResultado(org.mockito.ArgumentMatchers.eq("7"),
                org.mockito.ArgumentMatchers.argThat(datos -> datos.programa() == null
                        && datos.presupuesto() == null && datos.fechaInicio() == null
                        && datos.informeResultados() == null));
    }

    @Test
    void rechazaInstanciaSinResultado() {
        DelegateExecution execution = org.mockito.Mockito.mock(DelegateExecution.class);
        when(execution.getProcessInstanceBusinessKey()).thenReturn("42");

        assertThrows(IllegalStateException.class, () -> listener.notify(execution));
    }
}
