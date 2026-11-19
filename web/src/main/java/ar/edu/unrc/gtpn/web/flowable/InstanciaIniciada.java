package ar.edu.unrc.gtpn.web.flowable;

/**
 * Lo que devuelve Flowable al iniciar una instancia.
 *
 * @param id        ID de la instancia
 * @param terminada si la instancia ya terminó al iniciarse (el proceso termina enseguida cuando no es viable)
 */
public record InstanciaIniciada(String id, boolean terminada) {
}
