package ar.edu.unrc.gtpn.web.empresa;

import java.util.List;

import ar.edu.unrc.gtpn.web.flowable.Actividad;

/**
 * Un curso con el estado que ve la empresa y, si está en preparación, las actividades paralelas en curso (W-05).
 *
 * @param curso       datos del curso
 * @param estado      estado derivado de Flowable o el persistido por el proceso
 * @param actividades actividades de las ramas paralelas que están en curso; vacío fuera de la preparación
 * @param aula        aula física asignada al curso mientras está en marcha, o {@code null} si todavía no tiene o ya se
 *                    liberó
 */
public record CursoConEstado(CursoDeLaEmpresa curso, EstadoDelCurso estado, List<Actividad> actividades, String aula) {

    /** Un curso sin aula asignada. */
    public CursoConEstado(CursoDeLaEmpresa curso, EstadoDelCurso estado, List<Actividad> actividades) {
        this(curso, estado, actividades, null);
    }

    /** Si el curso tiene un aula asignada para mostrar. */
    public boolean tieneAula() {
        return aula != null && !aula.isBlank();
    }

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
