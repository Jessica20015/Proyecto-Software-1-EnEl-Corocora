package com.soft1.c7;

import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.soft1.c7.C7Expediente.datos;

/**
 * C7 · Evento final de mensaje "PQR atendida y causa raíz reportada" (MsgCausaRaizPQR).
 * Antes: topic "reportar-causa-raiz".
 */
@Component("reportarCausaRaiz")
public class ReportarCausaRaiz implements JavaDelegate {

    private final C7Expediente expediente;

    public ReportarCausaRaiz(C7Expediente expediente) {
        this.expediente = expediente;
    }

    @Override
    public void execute(DelegateExecution execution) {
        expediente.registrar(execution, "CAUSA_REPORTADA", "PQR atendida y causa raíz reportada", "sistema",
                datos("causa", Vars.obj(execution, "causaRaiz"), "asunto", Vars.obj(execution, "asunto"),
                        "destino", List.of("G1", "G3")));
        Vars.log("reportar-causa-raiz", execution, "causa " + Vars.str(execution, "causaRaiz")
                + " → G1 (causas agregadas) y G3 (causas recurrentes)");
    }
}
