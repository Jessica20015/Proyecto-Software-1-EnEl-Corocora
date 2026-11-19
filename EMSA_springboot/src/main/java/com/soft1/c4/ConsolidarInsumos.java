package com.soft1.c4;

import com.soft1.common.Fechas;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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

    private static final Pattern SUMINISTRO = Pattern.compile("SUM-\\d{6}");
    private static final Pattern PERIODO = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    @Override
    public void execute(DelegateExecution execution) {
        validarEntrada(execution);
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

    /**
     * Validación de la entrada del caso (consumo validado de C3, refacturación de C6/C7 o consumo corregido).
     * Corre en la misma transacción del mensaje: si un dato no tiene el tipo o el formato esperado, el mensaje
     * se rechaza con la lista de errores y no se crea (ni avanza) la instancia. Antes, un consumo no numérico
     * se convertía en 0 kWh sin avisar.
     */
    private static void validarEntrada(DelegateExecution execution) {
        // El estrato socioeconómico solo aplica al uso residencial (1 a 6); en los demás usos la tarifa no lo
        // considera y suele llegar 0 («no aplica»).
        boolean residencial = "RESIDENCIAL".equals(execution.getVariable("usoServicio"))
                || execution.getVariable("usoServicio") == null;
        new ValidacionC4(execution, "Entrada de C4")
                .patron("codigoSuministro", SUMINISTRO, "SUM-000000", true)
                .patron("periodo", PERIODO, "AAAA-MM", true)
                .numero("consumoKwh", 0.0, null, false, true)
                .numero("consumoPromedioKwh", 0.0, null, false, false)
                .opcion("tipoLectura", Set.of("REAL", "ESTIMADA"), false)
                .opcion("usoServicio", ValidacionC4.USOS_SERVICIO, false)
                .numero("estrato", residencial ? 1.0 : 0.0, 6.0, true, false)
                .numero("estimacionesConsecutivas", 0.0, null, true, false)
                .numero("devolucionesC3", 0.0, null, true, false)
                .verificar();
    }
}
