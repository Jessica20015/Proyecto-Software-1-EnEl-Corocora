package com.soft1.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/** Utilidades de fecha: mismo formato ISO-8601 (UTC, con milisegundos) que producía JavaScript con toISOString(). */
public final class Fechas {

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private Fechas() {
    }

    public static String iso(Instant instante) {
        return ISO.format(instante);
    }

    public static String ahora() {
        return iso(Instant.now());
    }

    /** Convierte a Instant un texto ISO (con 'Z' o con desfase, p. ej. -05:00), una fecha simple (yyyy-MM-dd) o un java.util.Date. */
    public static Instant parse(Object valor) {
        if (valor instanceof Date d) {
            return d.toInstant();
        }
        String s = String.valueOf(valor).trim();
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (Exception e) {
            return LocalDate.parse(s.substring(0, 10)).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
    }

    /** Texto de un valor de variable; si es una fecha de Java, se pasa a ISO-8601. */
    public static String texto(Object valor) {
        if (valor instanceof Date d) {
            return iso(d.toInstant());
        }
        return String.valueOf(valor);
    }
}
