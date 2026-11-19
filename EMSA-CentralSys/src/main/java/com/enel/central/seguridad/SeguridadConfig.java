package com.enel.central.seguridad;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Protege la API de tareas con {@link SesionInterceptor}; la página, los estáticos y /api/sesion quedan libres. */
@Configuration
public class SeguridadConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SesionInterceptor()).addPathPatterns("/task", "/task/**");
    }
}
