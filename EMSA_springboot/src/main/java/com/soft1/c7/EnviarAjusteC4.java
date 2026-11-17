package com.soft1.c7;

import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import static com.soft1.c7.C7Expediente.datos;

/**
 * C7 · Evento de mensaje (envío) "Ajuste autorizado enviado a C4" (MsgRefacturacionAutorizada).
 * Antes: topic "enviar-ajuste-c4".
 */
@Component("enviarAjusteC4")
public class EnviarAjusteC4 implements JavaDelegate {

    private final C7Expediente expediente;

    public EnviarAjusteC4(C7Expediente expediente) {
        this.expediente = expediente;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Vars.setBool(execution, "ajusteEnviadoC4", true);
        expediente.registrar(execution, "AJUSTE_AUTORIZADO_C4", "Ajuste autorizado enviado a C4",
                Vars.bool(execution, "silencioPositivo", false) ? "silencio administrativo positivo" : "analista de PQR",
                datos("valorAjuste", Vars.obj(execution, "valorAjuste") != null ? Vars.obj(execution, "valorAjuste") : 0,
                        "codigoSuministro", Vars.obj(execution, "codigoSuministro"),
                        "numeroFactura", Vars.str(execution, "numeroFactura", "")));
        Vars.log("enviar-ajuste-c4", execution, "MsgRefacturacionAutorizada → C4 por "
                + (Vars.tiene(execution, "valorAjuste") ? Vars.str(execution, "valorAjuste") : "0") + " COP");
    }
}
