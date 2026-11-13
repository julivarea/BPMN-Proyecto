package ar.edu.unrc.gtpn.web.propuestas;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Propuesta(
        Long id,
        Long cursoId,
        int version,
        String programa,
        BigDecimal presupuesto,
        String fechasPosibles,
        String observacionesEmpresa,
        LocalDateTime creadaEn
) {
    public Propuesta(Long cursoId, int version, String programa, BigDecimal presupuesto, String fechasPosibles, String observacionesEmpresa) {
        this(null, cursoId, version, programa, presupuesto, fechasPosibles, observacionesEmpresa, null);
    }
}
