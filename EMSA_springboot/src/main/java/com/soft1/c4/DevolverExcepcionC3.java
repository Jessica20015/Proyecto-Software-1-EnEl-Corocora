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
 * C4 · Evento intermedio de mensaje (envío) "Excepción devuelta a C3". Antes: topic "devolver-excepcion-c3".
 * Simula a C3: si simularRespuestaC3 = CORREGIDO (valor por defecto), C3 responde con el consumo corregido
 * unos segundos después (mensaje MsgConsumoCorregido).
 */
@Component("devolverExcepcionC3")
public class DevolverExcepcionC3 implements JavaDelegate {

    private final MensajeriaSimulada mensajeria;

    public DevolverExcepcionC3(MensajeriaSimulada mensajeria) {
        this.mensajeria = mensajeria;
    }

    @Override
    public void execute(DelegateExecution execution) {
        int devoluciones = Vars.entero(execution, "devolucionesC3", 0) + 1;
        Vars.setInt(execution, "devolucionesC3", devoluciones);
        Vars.setStr(execution, "fechaDevolucionC3", Fechas.ahora());
        Vars.log("devolver-excepcion-c3", execution, "excepción enviada a C3 (devolución #" + devoluciones + "): "
                + Vars.str(execution, "motivoExcepcion", ""));

        String respuesta = Vars.str(execution, "simularRespuestaC3", "CORREGIDO").toUpperCase();
        if ("CORREGIDO".equals(respuesta)) {
            double porDefecto = Vars.dbl(execution, "consumoPromedioKwh", 150) * 1.08;
            double corregido = Vars.dbl(execution, "consumoCorregidoKwh", porDefecto);

            Map<String, Object> variables = new HashMap<>();
            variables.put("consumoKwh", Variables.doubleValue(Math.round(corregido * 100) / 100.0));
            variables.put("tipoLectura", Variables.stringValue("REAL"));
            variables.put("estimacionesConsecutivas", Variables.integerValue(0));
            variables.put("fechaCorreccionC3", Variables.stringValue(Fechas.ahora()));
            mensajeria.porInstancia(4000, "MsgConsumoCorregido", execution.getProcessInstanceId(), variables);
        } else {
            Vars.log("devolver-excepcion-c3", execution,
                    "    ↳ C3 no responde (simulación): vencerá el temporizador del event-based gateway");
        }
    }
}
