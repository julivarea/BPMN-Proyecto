package ar.edu.unrc.gtpn.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Páginas de inicio de cada sección del sitio (enunciado 4.1). Cuando una sección tenga su propio controller, su
 * entrada se saca de acá.
 */
@Configuration
public class Secciones implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registro) {
        registro.addViewController("/").setViewName("index");
        registro.addViewController("/empresas").setViewName("empresa/inicio");
        registro.addViewController("/login").setViewName("login");
    }
}
