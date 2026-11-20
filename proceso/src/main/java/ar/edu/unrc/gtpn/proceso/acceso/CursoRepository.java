package ar.edu.unrc.gtpn.proceso.acceso;

/** Persistencia de los datos finales de un curso en la BD institucional. */
public interface CursoRepository {

    /**
     * Actualiza el resultado del curso.
     *
     * @param cursoId el {@code curso.id}, que es la business key de la instancia
     */
    void registrarResultado(long cursoId, DatosResultadoCurso datos);
}
