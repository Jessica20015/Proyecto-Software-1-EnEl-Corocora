package com.soft1.c4;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.delegate.VariableScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validación de entradas de C4 del lado del servidor.
 * <p>
 * Las reglas de los Camunda Forms (obligatorio, mínimo de caracteres, valores permitidos) solo se aplican en el
 * navegador: por REST, CentralSys o un mensaje de inicio puede llegar cualquier dato. Esta clase revisa el tipo
 * y el rango de cada variable, acumula todos los errores y, si hay alguno, rechaza la operación con un
 * {@link BadUserRequestException}: el inicio o la tarea no avanzan y quien envió los datos recibe el motivo.
 * <p>
 * Uso: {@code new ValidacionC4(execution, "Inicio de C4").patron(...).numero(...).verificar();}
 */
public final class ValidacionC4 {

    static final Set<String> USOS_SERVICIO = Set.of("RESIDENCIAL", "COMERCIAL", "INDUSTRIAL", "OFICIAL");

    private final VariableScope variables;
    private final String contexto;
    private final List<String> errores = new ArrayList<>();

    public ValidacionC4(VariableScope variables, String contexto) {
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
    public ValidacionC4 texto(String clave, int minimo) {
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

    /** Texto que debe cumplir una expresión regular. {@code formato} se muestra en el mensaje de error. */
    public ValidacionC4 patron(String clave, Pattern regex, String formato, boolean obligatorio) {
        Object v = valor(clave);
        if (vacio(v)) {
            if (obligatorio) {
                errores.add(clave + " es obligatorio");
            }
        } else if (!(v instanceof String s) || !regex.matcher(s).matches()) {
            errores.add(clave + " debe tener el formato " + formato + " (llegó «" + v + "»)");
        }
        return this;
    }

    /** Uno de los valores permitidos (los mismos del select o radio del formulario). */
    public ValidacionC4 opcion(String clave, Set<String> permitidos, boolean obligatorio) {
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

    /**
     * Número real (no un texto con dígitos) dentro de [min, max]; {@code null} en un límite = sin límite.
     * Con {@code entero} exige además que no tenga decimales (2 y 2.0 son válidos; 2.5 no).
     */
    public ValidacionC4 numero(String clave, Double min, Double max, boolean entero, boolean obligatorio) {
        Object v = valor(clave);
        if (v == null) {
            if (obligatorio) {
                errores.add(clave + " es obligatorio");
            }
            return this;
        }
        if (!(v instanceof Number n) || Double.isNaN(n.doubleValue()) || Double.isInfinite(n.doubleValue())) {
            errores.add(clave + " debe ser un número (llegó «" + v + "» de tipo " + v.getClass().getSimpleName() + ")");
            return this;
        }
        double d = n.doubleValue();
        if (entero && d != Math.rint(d)) {
            errores.add(clave + " debe ser un número entero (llegó " + v + ")");
        } else if ((min != null && d < min) || (max != null && d > max)) {
            errores.add(clave + " debe estar entre " + (min == null ? "-∞" : fmt(min)) + " y "
                    + (max == null ? "∞" : fmt(max)) + " (llegó " + v + ")");
        }
        return this;
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    /** Lanza la excepción con todos los errores encontrados, si los hay. */
    public void verificar() {
        if (!errores.isEmpty()) {
            throw new BadUserRequestException(contexto + ": datos no válidos: " + String.join("; ", errores) + ".");
        }
    }
}
