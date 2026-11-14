package ar.edu.unrc.gtpn.proceso.recursos;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifica *Reservar aula* (issue #19): se llama al web service y el aula queda en {@code aulaAsignada}. */
class ReservarAulaDelegateTest {

    private final ServicioAulas aulas = mock(ServicioAulas.class);
    private final DelegateExecution ejecucion = mock(DelegateExecution.class);
    private final ReservarAulaDelegate delegate = new ReservarAulaDelegate(aulas);

    @BeforeEach
    void laSolicitudEsLa7() {
        when(ejecucion.getProcessInstanceBusinessKey()).thenReturn("7");
    }

    @Test
    void reservaElAulaYLaGuardaEnLaVariableDelProceso() {
        when(aulas.reservar("7")).thenReturn("Aula 101");

        delegate.execute(ejecucion);

        verify(aulas).reservar("7");
        verify(ejecucion).setVariable("aulaAsignada", "Aula 101");
    }
}
