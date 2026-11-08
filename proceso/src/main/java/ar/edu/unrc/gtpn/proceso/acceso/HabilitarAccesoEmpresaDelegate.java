package ar.edu.unrc.gtpn.proceso.acceso;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Habilita el acceso de la empresa al sitio (enunciado 4.1.2). La creación de la cuenta queda para el issue #17. */
public class HabilitarAccesoEmpresaDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(HabilitarAccesoEmpresaDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        log.info("Acceso habilitado para la empresa de la solicitud {}", execution.getProcessInstanceBusinessKey());
        execution.setVariable("accesoHabilitado", true);
    }
}
