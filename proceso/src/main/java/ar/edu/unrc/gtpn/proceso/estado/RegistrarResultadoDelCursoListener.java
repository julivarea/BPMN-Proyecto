package ar.edu.unrc.gtpn.proceso.estado;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ar.edu.unrc.gtpn.proceso.acceso.CursoRepository;
import ar.edu.unrc.gtpn.proceso.acceso.CursoRepositoryJdbc;
import ar.edu.unrc.gtpn.proceso.acceso.DatosResultadoCurso;

/** Registra en la BD institucional cómo terminó la solicitud. */
public class RegistrarResultadoDelCursoListener implements ExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(RegistrarResultadoDelCursoListener.class);
    private final CursoRepository cursos;

    /** Constructor usado por Flowable al instanciar el listener desde el BPMN. */
    public RegistrarResultadoDelCursoListener() {
        this(CursoRepositoryJdbc.desdeEntorno());
    }

    /** Constructor para pruebas y para integrar otra implementación de persistencia. */
    public RegistrarResultadoDelCursoListener(CursoRepository cursos) {
        this.cursos = cursos;
    }

    @Override
    public void notify(DelegateExecution execution) {
        String resultado = (String) execution.getVariable("resultado");
        if (resultado == null || resultado.isBlank()) {
            throw new IllegalStateException("La instancia no tiene resultado final.");
        }
        String businessKey = execution.getProcessInstanceBusinessKey();
        if (businessKey == null || businessKey.isBlank()) {
            log.warn("No se registra el resultado {} porque la instancia no tiene businessKey.", resultado);
            return;
        }
        try {
            Long.parseLong(businessKey);
        } catch (NumberFormatException error) {
            log.warn("No se registra el resultado {} porque la businessKey no identifica un curso: {}",
                    resultado, businessKey);
            return;
        }
        cursos.registrarResultado(businessKey, new DatosResultadoCurso(
                resultado,
                (String) execution.getVariable("programa"),
                presupuesto(execution.getVariable("presupuesto")),
                fecha(execution.getVariable("fechaInicioCurso")),
                (String) execution.getVariable("informeResultados")));
    }

    private static BigDecimal presupuesto(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof BigDecimal) {
            return (BigDecimal) valor;
        }
        if (valor instanceof Number) {
            return new BigDecimal(valor.toString());
        }
        return new BigDecimal(valor.toString());
    }

    private static LocalDateTime fecha(Object valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.toString();
        try {
            // ISO con o sin desfase (2026-12-01T09:00:00 o 2026-12-01T09:00:00-03:00): se guarda la hora local.
            return LocalDateTime.parse(texto, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException noEsFechaConHora) {
            return LocalDate.parse(texto).atStartOfDay();
        }
    }
}
