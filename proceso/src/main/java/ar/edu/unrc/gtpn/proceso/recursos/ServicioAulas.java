package ar.edu.unrc.gtpn.proceso.recursos;

/**
 * Web service externo de aulas físicas (enunciado 4.1.3, I-10). La reserva y la liberación son automáticas y se
 * simulan; el resto del proceso solo conoce esta interfaz, así que cambiar la simulación por una llamada real no
 * toca los delegates.
 */
public interface ServicioAulas {

    /**
     * Reserva un aula para el curso.
     *
     * @param solicitud {@code businessKey} de la instancia, para poder rastrear la reserva
     * @return nombre o código del aula asignada (por ejemplo, {@code "Aula 101"})
     * @throws AulaNoDisponible si no queda ningún aula libre
     */
    String reservar(String solicitud);

    /** Libera el aula que se había reservado. */
    void liberar(String solicitud, String aula);
}
