package ar.edu.unrc.gtpn.web.solicitudes;

/** El CUIT ya está registrado con otra razón social: probablemente un error de tipeo (05-decisiones). */
public class CuitDeOtraEmpresa extends RuntimeException {

    public CuitDeOtraEmpresa() {
        super("Ese CUIT ya está registrado con otra razón social. Revisá el CUIT y la razón social.");
    }
}