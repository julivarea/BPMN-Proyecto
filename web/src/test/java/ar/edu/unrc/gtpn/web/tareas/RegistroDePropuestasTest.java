package ar.edu.unrc.gtpn.web.tareas;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.unrc.gtpn.web.propuestas.Propuesta;
import ar.edu.unrc.gtpn.web.propuestas.PropuestasRepository;

/**
 * Guardar la versión de la propuesta y completar la tarea de Flowable es una sola operación: la transacción es la de
 * Spring de verdad (un {@link TransactionTemplate}), con el administrador de transacciones doblado para ver si hubo
 * commit o rollback.
 */
class RegistroDePropuestasTest {

    private static final Map<String, Object> VARIABLES = Map.of("programa", "Excel avanzado");

    private final PropuestasRepository propuestas = mock(PropuestasRepository.class);
    private final BandejaDeTareas bandeja = mock(BandejaDeTareas.class);
    private final PlatformTransactionManager transacciones = mock(PlatformTransactionManager.class);
    private final TransactionStatus transaccion = mock(TransactionStatus.class);
    private final RegistroDePropuestas registro =
            new RegistroDePropuestas(propuestas, bandeja, new TransactionTemplate(transacciones));
    private final Propuesta propuesta =
            new Propuesta(10L, 1, "Excel avanzado", new BigDecimal("90000"), "Julio", null);

    RegistroDePropuestasTest() {
        when(transacciones.getTransaction(any(TransactionDefinition.class))).thenReturn(transaccion);
    }

    @Test
    void guardaLaVersionYDespuesCompletaLaTareaConfirmandoLaTransaccion() {
        registro.registrarYCompletar("jefe_admin", "t1", propuesta, VARIABLES);

        InOrder orden = inOrder(propuestas, bandeja, transacciones);
        orden.verify(propuestas).guardar(propuesta);
        orden.verify(bandeja).completar("jefe_admin", "t1", VARIABLES);
        orden.verify(transacciones).commit(transaccion);
        verify(transacciones, never()).rollback(any());
    }

    @Test
    void siFlowableNoCompletaLaTareaLaVersionGuardadaSeDescarta() {
        AccionNoPermitida rechazo = new AccionNoPermitida("Flowable no pudo completar la tarea.");
        doThrow(rechazo).when(bandeja).completar("jefe_admin", "t1", VARIABLES);

        AccionNoPermitida error = assertThrows(AccionNoPermitida.class,
                () -> registro.registrarYCompletar("jefe_admin", "t1", propuesta, VARIABLES));

        assertSame(rechazo, error);
        verify(transacciones).rollback(transaccion);
        verify(transacciones, never()).commit(any());
    }

    @Test
    void siElGuardadoFallaLaTareaNoSeCompleta() {
        doThrow(new IllegalStateException("la base no responde")).when(propuestas).guardar(propuesta);

        assertThrows(IllegalStateException.class,
                () -> registro.registrarYCompletar("jefe_admin", "t1", propuesta, VARIABLES));

        verify(bandeja, never()).completar(any(), any(), any());
        verify(transacciones).rollback(transaccion);
        verify(transacciones, never()).commit(any());
    }
}
