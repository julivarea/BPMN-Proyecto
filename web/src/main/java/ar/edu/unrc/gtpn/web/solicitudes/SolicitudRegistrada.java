package ar.edu.unrc.gtpn.web.solicitudes;

/**
 * Resultado de registrar una solicitud.
 *
 * @param cursoId id del curso creado, que es también la businessKey de su instancia
 * @param viable  {@code false} si el proceso terminó al iniciarse porque la institución ya tiene el máximo de cursos en
 *                ejecución (enunciado §3.1); la solicitud queda registrada pero no sigue adelante
 */
public record SolicitudRegistrada(long cursoId, boolean viable) {
}
