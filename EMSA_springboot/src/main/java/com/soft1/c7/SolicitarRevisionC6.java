package com.soft1.c7;

import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import static com.soft1.c7.C7Expediente.datos;

/**
 * C7 · Evento de mensaje (envío) "Revisión de la actuación solicitada a C6" (MsgRevisionActuacionC6).
 * Antes: topic "solicitar-revision-c6".
 */
@Component("solicitarRevisionC6")
public class SolicitarRevisionC6 implements JavaDelegate {

    private final C7Expediente expediente;

    public SolicitarRevisionC6(C7Expediente expediente) {
        this.expediente = expediente;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Vars.setBool(execution, "revisionSolicitadaC6", true);
        expediente.registrar(execution, "REVISION_SOLICITADA_C6", "Revisión de la actuación solicitada a C6",
                "analista de PQR", datos("causa", Vars.obj(execution, "causaRaiz")));
        Vars.log("solicitar-revision-c6", execution, "MsgRevisionActuacionC6 → C6");
    }
}
