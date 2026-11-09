package ar.edu.unrc.gtpn.proceso.viabilidad;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.runtime.ProcessInstance;

/** Cuenta los cursos que ya habilitaron el acceso de la empresa para la regla de viabilidad. */
public class ContarCursosEnEjecucionListener implements ExecutionListener {

    @Override
    public void notify(DelegateExecution execution) {
        ProcessInstance procesoActual = CommandContextUtil.getProcessEngineConfiguration()
                .getRuntimeService()
                .createProcessInstanceQuery()
                .processInstanceId(execution.getProcessInstanceId())
                .singleResult();

        long cursosEnEjecucion = CommandContextUtil.getProcessEngineConfiguration()
                .getRuntimeService()
                .createProcessInstanceQuery()
                .processDefinitionKey("organizarCurso")
                .variableValueEquals("accesoHabilitado", true)
                .list()
                .stream()
                .filter(instancia -> !instancia.getId().equals(procesoActual.getId()))
                .count();

        execution.setVariable("cursosEnEjecucion", cursosEnEjecucion);
    }
}
