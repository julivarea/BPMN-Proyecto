package ar.edu.unrc.gtpn.proceso.recursos;

/**
 * Plataforma de aulas virtuales (enunciado 4.1.3). El enunciado no exige un web service para la virtual: la
 * liberación también se simula, pero detrás de esta interfaz para poder probarla con un doble.
 */
public interface ServicioAulasVirtuales {

    /** Libera la plataforma virtual que se había asignado al curso. */
    void liberar(String solicitud);
}
