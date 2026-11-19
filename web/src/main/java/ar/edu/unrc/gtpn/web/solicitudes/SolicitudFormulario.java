package ar.edu.unrc.gtpn.web.solicitudes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos que carga una empresa al solicitar una capacitación (enunciado §4.1.1). Los límites de largo coinciden con las
 * columnas de empresa y curso (V1__esquema_inicial.sql).
 */
public record SolicitudFormulario(

        @NotBlank(message = "Ingresá el CUIT.")
        @Pattern(regexp = "\\d{2}-?\\d{8}-?\\d", message = "El CUIT tiene que tener 11 dígitos, por ejemplo 30-12345678-9.")
        String cuit,

        @NotBlank(message = "Ingresá la razón social.")
        @Size(max = 200, message = "La razón social puede tener hasta 200 caracteres.")
        String razonSocial,

        @NotBlank(message = "Ingresá el nombre de la persona de contacto.")
        @Size(max = 200, message = "El nombre puede tener hasta 200 caracteres.")
        String contactoNombre,

        @NotBlank(message = "Ingresá el email de contacto.")
        @Email(message = "El email no tiene un formato válido.")
        @Size(max = 200, message = "El email puede tener hasta 200 caracteres.")
        String contactoEmail,

        @Size(max = 50, message = "El teléfono puede tener hasta 50 caracteres.")
        String contactoTelefono,

        @NotBlank(message = "Ingresá la temática del curso.")
        @Size(max = 200, message = "La temática puede tener hasta 200 caracteres.")
        String nombre,

        @NotBlank(message = "Ingresá una descripción.")
        String descripcion,

        @NotNull(message = "Ingresá la cantidad estimada de participantes.")
        @Positive(message = "La cantidad de participantes tiene que ser mayor que cero.")
        Integer participantesEstimados,

        @NotBlank(message = "Describí las fechas preferidas.")
        String fechasPreferidas) {

        /** El mismo formulario, con el CUIT y la razón social de la empresa con sesión. */
        SolicitudFormulario conEmpresa(String cuit, String razonSocial) {
                return new SolicitudFormulario(cuit, razonSocial, contactoNombre, contactoEmail, contactoTelefono, nombre,
                        descripcion, participantesEstimados, fechasPreferidas);
        }        
}
