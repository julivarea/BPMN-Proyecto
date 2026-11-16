package ar.edu.unrc.gtpn.proceso.acceso;

/** Persistencia de los datos finales de un curso en la BD institucional. */
public interface CursoRepository {

    /**
     * Actualiza el resultado del curso identificado por su business key.
     *
     * @param businessKey normalmente el {@code curso.id}
     */
    void registrarResultado(String businessKey, DatosResultadoCurso datos);
}
