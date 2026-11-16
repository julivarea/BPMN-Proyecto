package ar.edu.unrc.gtpn.web.seguridad;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/** Qué secciones requieren qué rol. Los roles salen de los grupos de Flowable (ver AutenticacionContraFlowable). */
@Configuration
public class SeguridadConfig {

    static final String ADMINISTRACION = "ADMINISTRACION";
    static final String DOCENTES = "DOCENTES";
    static final String EMPRESA = "EMPRESA";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, ObjectProvider<AutenticacionEmpresa> empresa,
            AutenticacionContraFlowable flowable) throws Exception {
        empresa.ifAvailable(http::authenticationProvider);
        return http
                .authenticationProvider(flowable)
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers("/administracion/**").hasRole(ADMINISTRACION)
                        .requestMatchers("/docentes/**").hasRole(DOCENTES)
                        .requestMatchers("/empresas", "/empresas/**").hasRole(EMPRESA)
                        // Las pantallas de informe tienen un rol esperado: docencia lo elabora y administración lo envía.
                        .requestMatchers("/tareas/*/elaborar-informe").hasRole(DOCENTES)
                        .requestMatchers("/tareas/*/enviar-informe").hasRole(ADMINISTRACION)
                        .requestMatchers("/tareas/**").hasAnyRole(ADMINISTRACION, DOCENTES)
                        // La sección de empresas se protege con su propio login (issue #28).
                        .anyRequest().permitAll())
                .formLogin(login -> login.loginPage("/login").permitAll())
                .logout(salida -> salida.logoutSuccessUrl("/?salida"))
                .build();
    }
}
