package ar.edu.unrc.gtpn.proceso.acceso;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/** Datos del proceso que se conservan al cerrar un curso. */
public final class DatosResultadoCurso {

    private static final Set<String> ESTADOS_FINALES = Set.of(
            "NO_VIABLE", "SIN_RESPUESTA", "RECHAZADO", "CANCELADO", "FINALIZADO");

    private final String estado;
    private final String programa;
    private final BigDecimal presupuesto;
    private final LocalDateTime fechaInicio;
    private final String informeResultados;

    public DatosResultadoCurso(String estado, String programa, BigDecimal presupuesto, LocalDateTime fechaInicio,
            String informeResultados) {
        if (estado == null || !ESTADOS_FINALES.contains(estado)) {
            throw new IllegalArgumentException("Resultado final de curso inválido: " + estado);
        }
        this.estado = estado;
        this.programa = programa;
        this.presupuesto = presupuesto;
        this.fechaInicio = fechaInicio;
        this.informeResultados = informeResultados;
    }

    public String estado() {
        return estado;
    }

    public String programa() {
        return programa;
    }

    public BigDecimal presupuesto() {
        return presupuesto;
    }

    public LocalDateTime fechaInicio() {
        return fechaInicio;
    }

    public String informeResultados() {
        return informeResultados;
    }
}
