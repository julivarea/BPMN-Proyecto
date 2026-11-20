package ar.edu.unrc.gtpn.proceso.viabilidad;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.impl.util.CommandContextUtil;

/** Cuenta los cursos que ya habilitaron el acceso de la empresa para la regla de viabilidad. */
public class ContarCursosEnEjecucionListener implements ExecutionListener {

    @Override
    public void notify(DelegateExecution execution) {
        // El enunciado (3.1) excluye del conteo a la instancia que evalúa su propia viabilidad.
        String instanciaQueEvalua = execution.getProcessInstanceId();

        long cursosEnEjecucion = CommandContextUtil.getProcessEngineConfiguration()
                .getRuntimeService()
                .createProcessInstanceQuery()
                .processDefinitionKey("organizarCurso")
                .variableValueEquals("accesoHabilitado", true)
                .list()
                .stream()
                .filter(instancia -> !instancia.getId().equals(instanciaQueEvalua))
                .count();

        execution.setVariable("cursosEnEjecucion", cursosEnEjecucion);
    }
}
