package com.soft1.common;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.variable.Variables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lectura y escritura tipada de variables de proceso.
 * Equivale a las funciones v(), tiene(), str(), int(), dbl() y bool() que usaban los workers de Node.js.
 */
public final class Vars {

    private static final Logger LOG = LoggerFactory.getLogger("emsa");

    private Vars() {
    }

    // ------------------------------- lectura -------------------------------

    public static boolean tiene(DelegateExecution e, String nombre) {
        return e.getVariable(nombre) != null;
    }

    public static Object obj(DelegateExecution e, String nombre) {
        return e.getVariable(nombre);
    }

    public static String str(DelegateExecution e, String nombre, String porDefecto) {
        Object v = e.getVariable(nombre);
        return v == null ? porDefecto : Fechas.texto(v);
    }

    public static String str(DelegateExecution e, String nombre) {
        return str(e, nombre, null);
    }

    public static double dbl(DelegateExecution e, String nombre, double porDefecto) {
        Object v = e.getVariable(nombre);
        if (v == null) {
            return porDefecto;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException ex) {
            return porDefecto;
        }
    }

    public static int entero(DelegateExecution e, String nombre, int porDefecto) {
        return (int) dbl(e, nombre, porDefecto);
    }

    public static boolean bool(DelegateExecution e, String nombre, boolean porDefecto) {
        Object v = e.getVariable(nombre);
        if (v == null) {
            return porDefecto;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v).trim());
    }

    // ------------------------------ escritura ------------------------------

    public static void setStr(DelegateExecution e, String nombre, String valor) {
        e.setVariable(nombre, Variables.stringValue(valor));
    }

    public static void setDbl(DelegateExecution e, String nombre, double valor) {
        e.setVariable(nombre, Variables.doubleValue(valor));
    }

    public static void setInt(DelegateExecution e, String nombre, int valor) {
        e.setVariable(nombre, Variables.integerValue(valor));
    }

    public static void setBool(DelegateExecution e, String nombre, boolean valor) {
        e.setVariable(nombre, Variables.booleanValue(valor));
    }

    // -------------------------------- logs ---------------------------------

    /** Equivale a log(topic, task, msg) de los workers: [nombre] clave-de-negocio → mensaje. */
    public static void log(String nombre, DelegateExecution e, String mensaje) {
        String id = e.getProcessBusinessKey() != null ? e.getProcessBusinessKey() : e.getProcessInstanceId();
        LOG.info("[{}] {} → {}", nombre, id, mensaje);
    }
}
