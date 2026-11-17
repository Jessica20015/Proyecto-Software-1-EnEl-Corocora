package com.soft1.c4;

import com.soft1.common.Fechas;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * C4 · Service Task "Escalar verificación al coordinador" (temporizador no interrumpible).
 * Antes: topic "escalar-coordinador".
 */
@Component("escalarCoordinador")
public class EscalarCoordinador implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        Vars.setStr(execution, "fechaEscalamientoCalidad", Fechas.ahora());
        Vars.log("escalar-coordinador", execution,
                "SLA de verificación vencido: aviso enviado al coordinador de facturación");
    }
}
