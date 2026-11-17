package com.soft1.c2;

import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * C2 · Service Task "Documentar evento, causa y afectación" (DocumentarEvento).
 * Antes: worker de Node.js con topic "registro_afectacion_worker".
 */
@Component("registroAfectacion")
public class RegistroAfectacion implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String causa = Vars.str(execution, "causaFalla");
        String clientes = Vars.str(execution, "clientesAfectados");
        Vars.log("registro_afectacion", execution,
                "Causa: " + causa + " | Clientes afectados: " + clientes);

        Vars.setBool(execution, "eventoDocumentado", true);
        Vars.setStr(execution, "numeroRegistroEvento", "EVT-" + System.currentTimeMillis());
    }
}
