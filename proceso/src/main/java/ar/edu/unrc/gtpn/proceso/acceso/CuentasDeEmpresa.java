package ar.edu.unrc.gtpn.proceso.acceso;

import java.util.Optional;

/**
 * Acceso a las cuentas de acceso de las empresas en la BD de la institución (enunciado 4.1.2, issue #17).
 * Es una interfaz para poder probar el delegate con un doble, sin base de datos.
 */
public interface CuentasDeEmpresa {

    /**
     * Empresa dueña del curso, con su usuario si ya tiene cuenta. Vacío si el curso no existe.
     *
     * @param cursoId {@code curso.id}, que es el {@code businessKey} de la instancia
     */
    Optional<EmpresaDelCurso> buscarPorCurso(long cursoId);

    /**
     * Crea la cuenta de la empresa. La clave se guarda encriptada con BCrypt.
     *
     * @param empresaId    empresa dueña del curso
     * @param usuario      usuario generado, único en la tabla
     * @param passwordHash clave ya encriptada
     */
    void crear(long empresaId, String usuario, String passwordHash);
}
