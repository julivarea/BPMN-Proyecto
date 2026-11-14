package ar.edu.unrc.gtpn.proceso.recursos;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifica *Liberar aula virtual* (issue #19): la compensación libera la plataforma de la solicitud. */
class LiberarAulaVirtualDelegateTest {

    private final ServicioAulasVirtuales virtuales = mock(ServicioAulasVirtuales.class);
    private final DelegateExecution ejecucion = mock(DelegateExecution.class);
    private final LiberarAulaVirtualDelegate delegate = new LiberarAulaVirtualDelegate(virtuales);

    @BeforeEach
    void laSolicitudEsLa7() {
        when(ejecucion.getProcessInstanceBusinessKey()).thenReturn("7");
    }

    @Test
    void liberaElAulaVirtualDeLaSolicitud() {
        delegate.execute(ejecucion);

        verify(virtuales).liberar("7");
    }
}
