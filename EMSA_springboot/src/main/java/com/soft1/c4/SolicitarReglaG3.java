package com.soft1.c4;

import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * C4 · Evento final de mensaje "Solicitud de regla tarifaria enviada a G3". Antes: topic "solicitar-regla-g3".
 */
@Component("solicitarReglaG3")
public class SolicitarReglaG3 implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String periodo = Vars.str(execution, "periodoSinRegla", Vars.str(execution, "periodo"));
        Vars.log("solicitar-regla-g3", execution, "G3 ← solicitud de regla tarifaria para " + periodo
                + ": " + Vars.str(execution, "mensajeError", ""));
    }
}
