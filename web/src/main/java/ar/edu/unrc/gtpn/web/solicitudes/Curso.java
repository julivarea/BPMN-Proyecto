package ar.edu.unrc.gtpn.web.solicitudes;

public record Curso(
        Long id,
        Long empresaId,
        String razonSocialEmpresa,
        String nombre,
        String descripcion,
        int participantesEstimados,
        String fechasPreferidas,
        String contactoNombre,
        String contactoEmail,
        String contactoTelefono
) {}
