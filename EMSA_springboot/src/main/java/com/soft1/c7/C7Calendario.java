package com.soft1.c7;

import com.soft1.common.Fechas;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.DayOfWeek;
import java.util.Locale;
import java.util.Set;

/**
 * Calendario de días hábiles (Colombia). Festivos de 2026 y 2027 (Ley 51 de 1983, festivos trasladados al lunes).
 * Referencia académica: G3 debe validarla.
 * <p>
 * Modo "demo": los plazos en días hábiles se convierten en segundos (emsa.c7.segundos-por-dia, 30 por defecto).
 * Modo "produccion": días hábiles reales.
 */
@Component
public class C7Calendario {

    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota"); // UTC-5, sin horario de verano

    private static final Set<String> FESTIVOS = Set.of(
            // 2026
            "2026-01-01", "2026-01-12", "2026-03-23", "2026-04-02", "2026-04-03", "2026-05-01", "2026-05-18",
            "2026-06-08", "2026-06-15", "2026-06-29", "2026-07-20", "2026-08-07", "2026-08-17", "2026-10-12",
            "2026-11-02", "2026-11-16", "2026-12-08", "2026-12-25",
            // 2027
            "2027-01-01", "2027-01-11", "2027-03-22", "2027-03-25", "2027-03-26", "2027-05-01", "2027-05-10",
            "2027-05-31", "2027-06-07", "2027-07-05", "2027-07-20", "2027-08-07", "2027-08-16", "2027-10-18",
            "2027-11-01", "2027-11-15", "2027-12-08", "2027-12-25");

    private final boolean produccion;
    private final long segundosPorDia;

    public C7Calendario(@Value("${emsa.c7.modo:demo}") String modo,
                        @Value("${emsa.c7.segundos-por-dia:30}") long segundosPorDia) {
        this.produccion = "produccion".equalsIgnoreCase(modo);
        this.segundosPorDia = segundosPorDia;
    }

    public boolean esProduccion() {
        return produccion;
    }

    public long segundosPorDia() {
        return segundosPorDia;
    }

    private static boolean esHabil(LocalDate dia) {
        DayOfWeek d = dia.getDayOfWeek();
        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY && !FESTIVOS.contains(dia.toString());
    }

    /** Fecha límite tras n días hábiles contados desde el día siguiente (fin del día, hora de Colombia). */
    public String venceEnDiasHabiles(Instant desde, double n) {
        if (!produccion) {
            return Fechas.iso(desde.plusMillis((long) (n * segundosPorDia * 1000)));
        }
        LocalDate dia = desde.atZone(COLOMBIA).toLocalDate();
        int cuenta = 0;
        while (cuenta < n) {
            dia = dia.plusDays(1);
            if (esHabil(dia)) {
                cuenta++;
            }
        }
        return dia + "T23:59:59-05:00";
    }

    /** Momento de la alerta al 70 % del término. */
    public String alertaAl70(Instant desde, double termino) {
        return produccion ? venceEnDiasHabiles(desde, Math.ceil(termino * 0.7)) : venceEnDiasHabiles(desde, termino * 0.7);
    }

    /** Días hábiles transcurridos entre dos instantes (en demo, en "días" de segundosPorDia segundos). */
    public double diasHabilesEntre(Instant desde, Instant hasta) {
        if (!produccion) {
            double dias = (hasta.toEpochMilli() - desde.toEpochMilli()) / 1000.0 / segundosPorDia;
            return Math.round(dias * 10) / 10.0;
        }
        LocalDate dia = desde.atZone(COLOMBIA).toLocalDate();
        LocalDate fin = hasta.atZone(COLOMBIA).toLocalDate();
        int cuenta = 0;
        while (dia.isBefore(fin)) {
            dia = dia.plusDays(1);
            if (esHabil(dia)) {
                cuenta++;
            }
        }
        return cuenta;
    }

    /**
     * Fecha legible para los formularios (hora de Colombia), p. ej. «4 de diciembre de 2026 a las 11:59 p. m.».
     * Se arma a mano (espacios normales) para que el texto sea idéntico en cualquier JDK.
     */
    public String legible(String iso) {
        ZonedDateTime z = Fechas.parse(iso).atZone(COLOMBIA);
        String mes = z.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-CO"));
        int hora12 = z.getHour() % 12 == 0 ? 12 : z.getHour() % 12;
        String jornada = z.getHour() < 12 ? "a. m." : "p. m.";
        return String.format("%d de %s de %d a las %d:%02d %s",
                z.getDayOfMonth(), mes, z.getYear(), hora12, z.getMinute(), jornada);
    }
}
