package ar.edu.unrc.gtpn.web.solicitudes;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de las solicitudes (propiedades {@code solicitudes.*} de application.yml). El plazo de respuesta es
 * configurable
 */
@ConfigurationProperties("solicitudes")
public record SolicitudesProperties(String plazoRespuesta) {
}