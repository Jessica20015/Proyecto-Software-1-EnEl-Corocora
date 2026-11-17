package com.soft1.c7;

import com.soft1.common.Fechas;
import com.soft1.common.JsonlStore;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Expediente único de C7: un registro por evento (regla de trazabilidad §8.4) y consecutivo de radicados.
 */
@Component
public class C7Expediente {

    static final String ARCHIVO = "expediente-pqr.jsonl";
    static final String CONSECUTIVO = "consecutivo.txt";

    private final JsonlStore store;

    public C7Expediente(JsonlStore store) {
        this.store = store;
    }

    /** Construye un mapa ordenado a partir de pares clave, valor (admite valores null). */
    public static Map<String, Object> datos(Object... paresClaveValor) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < paresClaveValor.length; i += 2) {
            mapa.put((String) paresClaveValor[i], paresClaveValor[i + 1]);
        }
        return mapa;
    }

    public void registrar(DelegateExecution e, String evento, String actividad, String responsable,
                          Map<String, Object> datos) {
        Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("fechaHora", Fechas.ahora());
        registro.put("expedienteId", Vars.str(e, "expedienteId", e.getProcessBusinessKey()));
        registro.put("radicado", Vars.obj(e, "radicado"));
        registro.put("instancia", Vars.obj(e, "instancia"));
        registro.put("evento", evento);
        registro.put("actividad", actividad);
        registro.put("responsable", responsable);
        registro.put("estado", datos.get("estado"));
        registro.put("version", Vars.str(e, "versionRegla", "Reglas PQR G3 v1.0"));
        registro.put("causa", datos.get("causa"));
        registro.put("datos", datos);
        store.agregar(ARCHIVO, registro);
    }

    public List<Map<String, Object>> leer() {
        return store.leer(ARCHIVO);
    }

    /** Los consecutivos se asignan en serie (JsonlStore es synchronized) para no repetir radicados. */
    public String siguienteRadicado() {
        int n = store.siguienteConsecutivo(CONSECUTIVO);
        return String.format("PQR-%d-%06d", LocalDate.now().getYear(), n);
    }
}
