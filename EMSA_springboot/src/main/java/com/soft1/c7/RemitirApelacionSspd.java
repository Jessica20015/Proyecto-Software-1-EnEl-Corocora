package com.soft1.c7;

import com.soft1.common.Fechas;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import static com.soft1.c7.C7Expediente.datos;

/**
 * C7 · Evento de mensaje (envío) "Expediente remitido a la SSPD para la apelación" (MsgRemisionApelacionSSPD).
 * Antes: topic "remitir-apelacion-sspd".
 */
@Component("remitirApelacionSspd")
public class RemitirApelacionSspd implements JavaDelegate {

    private final C7Expediente expediente;

    public RemitirApelacionSspd(C7Expediente expediente) {
        this.expediente = expediente;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Vars.setStr(execution, "fechaRemisionSSPD", Fechas.ahora());
        expediente.registrar(execution, "REMITIDO_SSPD", "Expediente remitido a la SSPD para la apelación",
                "Oficina de PQR", datos("estado", "REMITIDO_SSPD", "copiaS4", true));
        Vars.log("remitir-apelacion-sspd", execution,
                "expediente completo → SSPD (apelación) · copia → S4 (defensa)");
    }
}
