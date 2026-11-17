package com.soft1.c7;

import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import static com.soft1.c7.C7Expediente.datos;

/**
 * C7 · Service Task "Alertar al coordinador de PQR" (alerta no interrumpible al 70 % del término).
 * Antes: topic "alertar-coordinador".
 */
@Component("alertarCoordinador")
public class AlertarCoordinador implements JavaDelegate {

    private final C7Expediente expediente;

    public AlertarCoordinador(C7Expediente expediente) {
        this.expediente = expediente;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Vars.setBool(execution, "alertaTerminoEmitida", true);
        expediente.registrar(execution, "ALERTA_TERMINO", "Alertar al coordinador de PQR", "sistema",
                datos("fechaVencimiento", Vars.obj(execution, "fechaVencimiento")));
        Vars.log("alertar-coordinador", execution,
                "⚠ 70 % del término consumido; vence " + Vars.str(execution, "fechaVencimiento"));
    }
}
