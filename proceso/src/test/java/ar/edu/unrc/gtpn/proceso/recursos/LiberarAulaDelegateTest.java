package ar.edu.unrc.gtpn.proceso.recursos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifica *Liberar aula* (issue #19): lee {@code aulaAsignada} y la devuelve al web service. */
class LiberarAulaDelegateTest {

    private final ServicioAulas aulas = mock(ServicioAulas.class);
    private final DelegateExecution ejecucion = mock(DelegateExecution.class);
    private final LiberarAulaDelegate delegate = new LiberarAulaDelegate(aulas);

    @BeforeEach
    void laSolicitudEsLa7() {
        when(ejecucion.getProcessInstanceBusinessKey()).thenReturn("7");
    }

    @Test
    void liberaElAulaQueLaInstanciaTeniaAsignada() {
        when(ejecucion.getVariable("aulaAsignada")).thenReturn("Aula 101");

        delegate.execute(ejecucion);

        verify(aulas).liberar("7", "Aula 101");
    }

    @Test
    void siNoSeReservoAulaNoLlamaAlWebService() {
        delegate.execute(ejecucion);

        verify(aulas, never()).liberar(anyString(), any());
    }
}
