package ar.edu.unrc.gtpn.proceso.viabilidad;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;

/** Entrada de la regla de viabilidad (enunciado 3.1). El conteo real queda para el issue #11. */
public class ContarCursosEnEjecucionListener implements ExecutionListener {

    @Override
    public void notify(DelegateExecution execution) {
        execution.setVariable("cursosEnEjecucion", 0);
    }
}
