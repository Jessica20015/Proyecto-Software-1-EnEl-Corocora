package com.enel.central.seguridad;

import java.io.Serializable;
import java.util.List;

/**
 * Funcionario con sesión iniciada en CentralSys: usuario de Camunda y sus grupos de negocio (carriles).
 * Se guarda en la sesión HTTP tras verificar usuario y clave contra el motor.
 */
public record Funcionario(String usuario, List<String> grupos) implements Serializable {

    public static final String ATRIBUTO_SESION = "funcionario";

    public boolean perteneceA(String grupo) {
        return grupos.contains(grupo);
    }
}
