package ar.edu.unrc.gtpn.web.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Qué secciones requieren qué rol. Los roles salen de los grupos de Flowable (ver AutenticacionContraFlowable). */
@Configuration
public class SeguridadConfig {

    static final String ADMINISTRACION = "ADMINISTRACION";
    static final String DOCENTES = "DOCENTES";

    @Bean
    SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers("/administracion/**").hasRole(ADMINISTRACION)
                        .requestMatchers("/docentes/**").hasRole(DOCENTES)
                        // La sección de empresas se protege con su propio login (issue #28).
                        .anyRequest().permitAll())
                .formLogin(login -> login.loginPage("/login"))
                .logout(salida -> salida.logoutSuccessUrl("/?salida"))
                .build();
    }
}
