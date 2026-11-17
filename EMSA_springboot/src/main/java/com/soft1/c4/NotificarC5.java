package com.soft1.c4;

import com.soft1.common.Fechas;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * C4 · Evento intermedio de mensaje (envío) "Cuenta por cobrar enviada a C5". Antes: topic "notificar-c5".
 */
@Component("notificarC5")
public class NotificarC5 implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        Vars.setStr(execution, "fechaNotificacionC5", Fechas.ahora());
        String tipo = Vars.bool(execution, "esRefacturacion", false)
                ? "NUEVA VERSIÓN de la cuenta existente" : "cuenta nueva";
        Vars.log("notificar-c5", execution, "C5 ← " + Vars.str(execution, "cuentaPorCobrarId")
                + " (" + tipo + ") por " + Vars.str(execution, "valorTotal")
                + " COP, vence " + Vars.str(execution, "fechaLimitePago"));
    }
}
