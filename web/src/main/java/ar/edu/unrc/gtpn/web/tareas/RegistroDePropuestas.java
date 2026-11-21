package ar.edu.unrc.gtpn.web.tareas;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import ar.edu.unrc.gtpn.web.propuestas.Propuesta;
import ar.edu.unrc.gtpn.web.propuestas.PropuestasRepository;

/**
 * Registra una versión de la propuesta y completa la tarea de Flowable que la produce como una sola operación
 * (docs/01 W-07, docs/07 §3), para que el proceso no avance sin que la versión exista en la base: el PDF y las
 * revisiones siguientes dependen de ella.
 */
@Service
public class RegistroDePropuestas {

    private final PropuestasRepository propuestas;
    private final BandejaDeTareas bandeja;
    private final TransactionOperations transacciones;

    public RegistroDePropuestas(PropuestasRepository propuestas, BandejaDeTareas bandeja,
            TransactionOperations transacciones) {
        this.propuestas = propuestas;
        this.bandeja = bandeja;
        this.transacciones = transacciones;
    }

    /**
     * Inserta la versión y recién después completa la tarea, dentro de una transacción de la base de la institución:
     * si Flowable no puede completar la tarea (o la bandeja la rechaza), la versión se descarta; y si el guardado
     * falla, la tarea no se completa. Flowable es un servicio aparte, así que no puede participar de la transacción:
     * queda sin cubrir solo el caso, mucho más raro, de que falle el commit después de completar la tarea.
     */
    void registrarYCompletar(String usuario, String tareaId, Propuesta propuesta, Map<String, Object> variables) {
        transacciones.executeWithoutResult(estado -> {
            propuestas.guardar(propuesta);
            bandeja.completar(usuario, tareaId, variables);
        });
    }
}
