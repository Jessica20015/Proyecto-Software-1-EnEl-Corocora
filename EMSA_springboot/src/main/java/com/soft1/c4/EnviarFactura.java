package com.soft1.c4;

import com.soft1.common.Fechas;
import com.soft1.common.MensajeriaSimulada;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.variable.Variables;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * C4.1 (C4_EntregarFactura) · Service Task "Enviar factura por el canal asignado". Antes: topic "enviar-factura".
 * Simula al operador de entrega según la variable simularResultadoEntrega:
 * CONFIRMADA | DEVUELTA_LUEGO_CONFIRMADA | DEVUELTA | SIN_RESPUESTA
 */
@Component("enviarFactura")
public class EnviarFactura implements JavaDelegate {

    private final MensajeriaSimulada mensajeria;

    public EnviarFactura(MensajeriaSimulada mensajeria) {
        this.mensajeria = mensajeria;
    }

    @Override
    public void execute(DelegateExecution execution) {
        int intento = Vars.entero(execution, "intentosEntrega", 0) + 1;
        String canal = intento == 1 ? Vars.str(execution, "canalPreferido", "DIGITAL") : "FISICO";

        Vars.setInt(execution, "intentosEntrega", intento);
        Vars.setStr(execution, "canalEntrega", canal);
        Vars.setStr(execution, "fechaEnvio", Fechas.ahora());
        Vars.setStr(execution, "ultimoResultadoEntrega", "SIN_RESPUESTA_DEL_OPERADOR");
        Vars.log("enviar-factura", execution,
                "intento " + intento + " por canal " + canal + " (" + Vars.str(execution, "numeroFactura") + ")");

        String escenario = Vars.str(execution, "simularResultadoEntrega", "CONFIRMADA").toUpperCase();
        String resultado = "NINGUNO";
        switch (escenario) {
            case "CONFIRMADA" -> resultado = "CONFIRMADA";
            case "DEVUELTA" -> resultado = "DEVUELTA";
            case "DEVUELTA_LUEGO_CONFIRMADA" -> resultado = intento == 1 ? "DEVUELTA" : "CONFIRMADA";
            default -> {
            }
        }

        String instancia = execution.getProcessInstanceId();
        Map<String, Object> variables = new HashMap<>();
        if ("CONFIRMADA".equals(resultado)) {
            variables.put("ultimoResultadoEntrega", Variables.stringValue("CONFIRMADA"));
            variables.put("soporteEntrega", Variables.stringValue(
                    ("DIGITAL".equals(canal) ? "ACUSE-" : "GUIA-") + System.currentTimeMillis()));
            mensajeria.porInstancia(3000, "MsgConfirmacionEntrega", instancia, variables);
        } else if ("DEVUELTA".equals(resultado)) {
            variables.put("ultimoResultadoEntrega", Variables.stringValue("DIGITAL".equals(canal)
                    ? "DEVUELTA: buzón inexistente" : "DEVUELTA: dirección no encontrada"));
            mensajeria.porInstancia(3000, "MsgDevolucionFactura", instancia, variables);
        } else {
            Vars.log("enviar-factura", execution,
                    "    ↳ el operador no responde (simulación): vencerá el plazo de entrega");
        }
    }
}
