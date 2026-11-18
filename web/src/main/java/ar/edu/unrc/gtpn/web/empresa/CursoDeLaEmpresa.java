package ar.edu.unrc.gtpn.web.empresa;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Curso de una empresa, con los datos que se le muestran en el portal (W-04). El estado guardado es el que deja el
 * proceso al terminar; mientras la instancia está activa, el portal lo deriva de Flowable.
 *
 * @param id                     ID del curso, que es el {@code businessKey} de su instancia
 * @param nombre                 temática solicitada
 * @param descripcion            descripción de la solicitud
 * @param participantesEstimados cantidad estimada de participantes
 * @param fechasPreferidas       fechas pedidas por la empresa
 * @param programa               programa acordado, o {@code null} si todavía no hay propuesta aceptada
 * @param presupuesto            presupuesto acordado, o {@code null} si todavía no hay propuesta aceptada
 * @param fechaInicio            fecha de inicio acordada, o {@code null}
 * @param informeResultados      informe de resultados, o {@code null}
 * @param estado                 estado persistido por el proceso
 * @param instanciaId            instancia de Flowable, o {@code null} si no se pudo iniciar
 * @param creadoEn               cuándo se envió la solicitud
 */
public record CursoDeLaEmpresa(
        long id,
        String nombre,
        String descripcion,
        int participantesEstimados,
        String fechasPreferidas,
        String programa,
        BigDecimal presupuesto,
        LocalDateTime fechaInicio,
        String informeResultados,
        String estado,
        String instanciaId,
        LocalDateTime creadoEn) {

    /**
     * El mismo curso con lo que el proceso ya definió pero guarda en la base recién al terminar: lo acordado al
     * aceptar la propuesta y el informe de resultados.
     */
    public CursoDeLaEmpresa conAcuerdo(LocalDateTime fechaInicio, String programa, BigDecimal presupuesto,
            String informe) {
        return new CursoDeLaEmpresa(id, nombre, descripcion, participantesEstimados, fechasPreferidas, programa,
                presupuesto, fechaInicio, informe, estado, instanciaId, creadoEn);
    }
}
