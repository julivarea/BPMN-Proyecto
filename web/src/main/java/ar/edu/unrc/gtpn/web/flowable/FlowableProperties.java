package ar.edu.unrc.gtpn.web.flowable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Conexión con la API REST de Flowable (propiedades {@code flowable.*} de application.yml). */
@ConfigurationProperties("flowable")
public record FlowableProperties(String url, String usuario, String clave) {
}
