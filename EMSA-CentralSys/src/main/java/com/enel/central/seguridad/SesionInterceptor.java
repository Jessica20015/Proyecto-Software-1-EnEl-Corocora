package com.enel.central.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Exige sesión iniciada para la API de tareas (/task/**). Sin sesión responde 401 en JSON y la interfaz
 * vuelve a la pantalla de ingreso.
 */
public class SesionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        HttpSession sesion = request.getSession(false);
        if (sesion != null && sesion.getAttribute(Funcionario.ATRIBUTO_SESION) instanceof Funcionario) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"Inicie sesión para consultar o gestionar tareas.\"}");
        return false;
    }
}
