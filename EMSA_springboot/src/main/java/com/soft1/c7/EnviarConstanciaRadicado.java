package com.soft1.c7;

import com.soft1.common.Fechas;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import static com.soft1.c7.C7Expediente.datos;

/**
 * C7 · Evento intermedio de mensaje (envío) "Constancia de radicado enviada". Antes: topic "enviar-constancia-radicado".
 */
@Component("enviarConstanciaRadicado")
public class EnviarConstanciaRadicado implements JavaDelegate {

    private final C7Expediente expediente;

    public EnviarConstanciaRadicado(C7Expediente expediente) {
        this.expediente = expediente;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Vars.setStr(execution, "fechaConstancia", Fechas.ahora());
        expediente.registrar(execution, "CONSTANCIA_ENVIADA", "Constancia de radicado enviada al usuario", "sistema",
                datos("canal", Vars.bool(execution, "autorizaNotificacionElectronica", false)
                                ? "correo " + Vars.str(execution, "correoNotificacion", "")
                                : "constancia impresa o SMS",
                        "fechaVencimiento", Vars.obj(execution, "fechaVencimiento")));
        Vars.log("enviar-constancia-radicado", execution, "constancia " + Vars.str(execution, "radicado")
                + " · vence " + Vars.str(execution, "fechaVencimiento"));
    }
}
