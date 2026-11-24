package ar.edu.unrc.gtpn.proceso.recursos;

/** El web service de aulas no tiene ninguna aula libre (enunciado, escenario 7). */
public class AulaNoDisponible extends RuntimeException {

    public AulaNoDisponible(String mensaje) {
        super(mensaje);
    }
}
