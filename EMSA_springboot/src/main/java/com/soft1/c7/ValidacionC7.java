package com.soft1.c7;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.delegate.VariableScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validación de entradas de C7 del lado del servidor.
 * <p>
 * Las reglas de los Camunda Forms (obligatorio, mínimo de caracteres, patrón, valores permitidos) solo se
 * aplican en el navegador: por REST, el portal (MsgPQRPortal) o CentralSys puede llegar cualquier dato.
 * Esta clase revisa el tipo, el formato y el rango de cada variable, acumula todos los errores y, si hay
 * alguno, rechaza la operación con un {@link BadUserRequestException}: la PQR no se radica (o la tarea no se
 * completa) y quien envió los datos recibe el motivo.
 */
public final class ValidacionC7 {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final VariableScope variables;
    private final String contexto;
    private final List<String> errores = new ArrayList<>();

    public ValidacionC7(VariableScope variables, String contexto) {
        this.variables = variables;
        this.contexto = contexto;
    }

    private Object valor(String clave) {
        return variables.getVariable(clave);
    }

    private static boolean vacio(Object v) {
        return v == null || (v instanceof String s && s.isBlank());
    }

    /** Texto obligatorio de al menos {@code minimo} caracteres (sin contar espacios de los extremos). */
    public ValidacionC7 texto(String clave, int minimo) {
        Object v = valor(clave);
        if (vacio(v)) {
            errores.add(clave + " es obligatorio");
        } else if (!(v instanceof String s)) {
            errores.add(clave + " debe ser texto");
        } else if (s.trim().length() < minimo) {
            errores.add(clave + " debe tener al menos " + minimo + " caracteres");
        }
        return this;
    }

    /** Texto que debe cumplir una expresión regular; vacío solo se acepta si no es obligatorio. */
    public ValidacionC7 patron(String clave, Pattern regex, String formato, boolean obligatorio) {
        Object v = valor(clave);
        if (vacio(v)) {
            if (obligatorio) {
                errores.add(clave + " es obligatorio");
            }
        } else if (!(v instanceof String s) || !regex.matcher(s.trim()).matches()) {
            errores.add(clave + " debe tener el formato " + formato + " (llegó «" + v + "»)");
        }
        return this;
    }

    /** Uno de los valores permitidos (los mismos del select o radio del formulario). */
    public ValidacionC7 opcion(String clave, Set<String> permitidos, boolean obligatorio) {
        Object v = valor(clave);
        if (vacio(v)) {
            if (obligatorio) {
                errores.add(clave + " es obligatorio");
            }
        } else if (!(v instanceof String s) || !permitidos.contains(s)) {
            errores.add(clave + " debe ser uno de " + permitidos.stream().sorted().toList() + " (llegó «" + v + "»)");
        }
        return this;
    }

    /** Número real (no un texto con dígitos) mayor o igual a {@code min}. */
    public ValidacionC7 numero(String clave, double min, boolean obligatorio) {
        Object v = valor(clave);
        if (v == null) {
            if (obligatorio) {
                errores.add(clave + " es obligatorio");
            }
        } else if (!(v instanceof Number n) || Double.isNaN(n.doubleValue()) || Double.isInfinite(n.doubleValue())) {
            errores.add(clave + " debe ser un número (llegó «" + v + "» de tipo " + v.getClass().getSimpleName() + ")");
        } else if (n.doubleValue() < min) {
            errores.add(clave + " debe ser mayor o igual a " + (long) min + " (llegó " + v + ")");
        }
        return this;
    }

    /**
     * Booleano real (true/false, no un texto). Con {@code debeSerTrue} exige además que esté marcado
     * (casillas obligatorias del formulario, como la autorización de tratamiento de datos).
     */
    public ValidacionC7 booleano(String clave, boolean obligatorio, boolean debeSerTrue) {
        Object v = valor(clave);
        if (v == null) {
            if (obligatorio || debeSerTrue) {
                errores.add(clave + " es obligatorio");
            }
        } else if (!(v instanceof Boolean b)) {
            errores.add(clave + " debe ser verdadero o falso (llegó «" + v + "» de tipo " + v.getClass().getSimpleName() + ")");
        } else if (debeSerTrue && !b) {
            errores.add(clave + " debe estar marcado");
        }
        return this;
    }

    /** Correo electrónico obligatorio con formato válido. */
    public ValidacionC7 correo(String clave) {
        return patron(clave, EMAIL, "usuario@dominio", true);
    }

    /** Lanza la excepción con todos los errores encontrados, si los hay. */
    public void verificar() {
        if (!errores.isEmpty()) {
            throw new BadUserRequestException(contexto + ": datos no válidos: " + String.join("; ", errores) + ".");
        }
    }
}
