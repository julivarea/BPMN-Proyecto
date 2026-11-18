package ar.edu.unrc.gtpn.comunicaciones;

import java.time.Instant;

/**
 * Solicitud de curso en curso, es decir, una instancia activa del proceso.
 *
 * @param instanciaId ID de la instancia en Flowable
 * @param clave       businessKey de la instancia (el ID del curso en la base de la institución)
 * @param iniciada    cuándo llegó la solicitud
 * @param curso       nombre del curso solicitado (variable {@code nombreCurso}), o {@code null} si la instancia no lo tiene
 * @param version     última versión de la propuesta enviada a la empresa (variable {@code versionPropuesta}); 0 si todavía no se envió ninguna
 */
record Solicitud(String instanciaId, String clave, Instant iniciada, String curso, int version) {

    Solicitud(String instanciaId, String clave, Instant iniciada) {
        this(instanciaId, clave, iniciada, null, 0);
    }

    /** Cómo se muestra la solicitud en la lista: {@code 42 · Excel avanzado · v2}. */
    String descripcion() {
        StringBuilder texto = new StringBuilder(clave);
        if (curso != null && !curso.isBlank()) {
            texto.append(" · ").append(curso);
        }
        if (version > 0) {
            texto.append(" · v").append(version);
        }
        return texto.toString();
    }
}
