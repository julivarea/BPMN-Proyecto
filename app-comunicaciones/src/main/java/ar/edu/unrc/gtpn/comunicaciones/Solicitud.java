package ar.edu.unrc.gtpn.comunicaciones;

import java.time.Instant;

/**
 * Solicitud de curso en curso, es decir, una instancia activa del proceso.
 *
 * @param instanciaId ID de la instancia en Flowable
 * @param clave       businessKey de la instancia (el ID del curso en la base de la institución)
 * @param iniciada    cuándo llegó la solicitud
 */
record Solicitud(String instanciaId, String clave, Instant iniciada) {
}
