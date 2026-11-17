package com.soft1.c4;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Configuración de C4: "repositorio de reglas tarifarias versionadas" (data store de G3, simulado)
 * y plazos de los temporizadores del modelo (ISO-8601).
 */
@Component
public class C4Config {

    /** CU = costo unitario de prestación del servicio (COP/kWh) por período. */
    public record Regla(String versionRegla, double cuKwh) {
    }

    private static final Map<String, Regla> REGLAS_TARIFARIAS = Map.of(
            "2026-09", new Regla("CU-2026-09 v1 (aprobada por G3)", 1008.42),
            "2026-10", new Regla("CU-2026-10 v1 (aprobada por G3)", 1012.37),
            "2026-11", new Regla("CU-2026-11 v2 (aprobada por G3)", 1019.85),
            "2026-12", new Regla("CU-2026-12 v1 (aprobada por G3)", 1024.1));

    private final Map<String, String> plazos;

    public C4Config(@Value("${emsa.c4.modo:demo}") String modo) {
        // En demo los plazos son cortos para poder ver todos los caminos del modelo.
        if ("produccion".equalsIgnoreCase(modo)) {
            plazos = Map.of("plazoEsperaC3", "P2D", "plazoSlaCalidad", "PT8H", "plazoEntrega", "P3D");
        } else {
            plazos = Map.of("plazoEsperaC3", "PT1M", "plazoSlaCalidad", "PT2M", "plazoEntrega", "PT1M");
        }
    }

    /** @return la regla vigente del período, o null si no existe una versión aprobada. */
    public Regla regla(String periodo) {
        return periodo == null ? null : REGLAS_TARIFARIAS.get(periodo);
    }

    public Map<String, String> plazos() {
        return plazos;
    }
}
