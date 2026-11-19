package ar.edu.unrc.gtpn.web.empresa;

import java.util.List;

import ar.edu.unrc.gtpn.web.flowable.Actividad;

/**
 * Un curso con el estado que ve la empresa y, si está en preparación, las actividades paralelas en curso (W-05).
 *
 * @param curso       datos del curso
 * @param estado      estado derivado de Flowable o el persistido por el proceso
 * @param actividades actividades de las ramas paralelas que están en curso; vacío fuera de la preparación
 */
public record CursoConEstado(CursoDeLaEmpresa curso, EstadoDelCurso estado, List<Actividad> actividades) {

    /** Si hay actividades paralelas para mostrar en el detalle. */
    public boolean muestraActividades() {
        return !actividades.isEmpty();
    }

    /** Si el curso ya terminó (o solo falta liberar recursos) y tiene informe de resultados para consultar. */
    public boolean tieneInforme() {
        return (estado == EstadoDelCurso.FINALIZADO || estado == EstadoDelCurso.EN_CIERRE)
                && curso.informeResultados() != null
                && !curso.informeResultados().isBlank();
    }
}
