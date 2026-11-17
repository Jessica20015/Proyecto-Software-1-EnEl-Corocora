package com.soft1.c4;

import com.soft1.common.Fechas;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * C4 · Service Task "Consolidar insumos de facturación" (Activity_ConsolidarInsumos).
 * Antes: topic "consolidar-insumos". Si no hay regla tarifaria vigente lanza el error BPMN
 * ERR_REGLA_NO_VIGENTE, que atrapa el evento límite "Regla tarifaria vigente no encontrada".
 */
@Component("consolidarInsumos")
public class ConsolidarInsumos implements JavaDelegate {

    private final C4Config config;

    public ConsolidarInsumos(C4Config config) {
        this.config = config;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String periodo = Vars.str(execution, "periodo");
        C4Config.Regla regla = config.regla(periodo);
        if (regla == null) {
            Vars.log("consolidar-insumos", execution,
                    "sin regla tarifaria vigente para " + periodo + " → error BPMN");
            // Las variables fijadas antes de lanzar el error quedan guardadas (equivale a las vars de handleBpmnError)
            Vars.setStr(execution, "periodoSinRegla", periodo);
            throw new BpmnError("ERR_REGLA_NO_VIGENTE",
                    "No existe una versión de la regla tarifaria aprobada por G3 para el período " + periodo);
        }

        double consumo = Vars.dbl(execution, "consumoKwh", 0);
        double promedio = Vars.dbl(execution, "consumoPromedioKwh", 150); // histórico de C3 (simulado si no llega)
        double desviacion = promedio > 0
                ? Math.abs(consumo - promedio) / promedio * 100
                : (consumo > 0 ? 999 : 0);

        Vars.setStr(execution, "versionRegla", regla.versionRegla());
        Vars.setDbl(execution, "cuKwh", regla.cuKwh());
        Vars.setDbl(execution, "consumoPromedioKwh", promedio);
        Vars.setDbl(execution, "desviacionAbsPct", Math.round(desviacion * 100) / 100.0);
        Vars.setDbl(execution, "consumoFacturableKwh", consumo);
        Vars.setBool(execution, "facturaProvisional", false);

        // Inicialización de contadores y datos que el modelo necesita (solo si no existen)
        Map<String, String> textosPorDefecto = Map.of(
                "tipoLectura", "REAL", "canalPreferido", "DIGITAL", "usoServicio", "RESIDENCIAL");
        textosPorDefecto.forEach((k, val) -> {
            if (!Vars.tiene(execution, k)) {
                Vars.setStr(execution, k, val);
            }
        });
        for (String k : new String[]{"estimacionesConsecutivas", "devolucionesC3", "correccionesPreEmision", "intentosEntrega"}) {
            if (!Vars.tiene(execution, k)) {
                Vars.setInt(execution, k, 0);
            }
        }
        if (!Vars.tiene(execution, "maxIntentosEntrega")) {
            Vars.setInt(execution, "maxIntentosEntrega", 2);
        }
        if (!Vars.tiene(execution, "estrato")) {
            Vars.setDbl(execution, "estrato", 4);
        }
        if (!Vars.tiene(execution, "fechaLiberacionC3")) {
            Vars.setStr(execution, "fechaLiberacionC3", Fechas.ahora()); // t0 del indicador
        }
        config.plazos().forEach((k, val) -> {
            if (!Vars.tiene(execution, k)) {
                Vars.setStr(execution, k, val);
            }
        });

        Vars.log("consolidar-insumos", execution, String.format(
                "consumo %s kWh, promedio %s kWh, desviación %.2f %%, regla %s",
                consumo, promedio, desviacion, regla.versionRegla()));
    }
}
