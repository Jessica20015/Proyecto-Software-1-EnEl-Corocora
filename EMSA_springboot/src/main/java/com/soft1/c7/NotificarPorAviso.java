package com.soft1.c7;

import com.soft1.common.Fechas;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import static com.soft1.c7.C7Expediente.datos;

/**
 * C7.1 (C7_NotificarDecision) · Service Task "Notificar por aviso con copia del acto".
 * Antes: topic "notificar-por-aviso".
 */
@Component("notificarPorAviso")
public class NotificarPorAviso implements JavaDelegate {

    private final C7Expediente expediente;

    public NotificarPorAviso(C7Expediente expediente) {
        this.expediente = expediente;
    }

    @Override
    public void execute(DelegateExecution execution) {
        boolean reposicion = "REPOSICION".equals(Vars.str(execution, "instancia"));
        String estado = reposicion ? Vars.str(execution, "simularAvisoReposicion") : null;
        if (estado == null) {
            estado = Vars.str(execution, "simularAviso", "ENTREGADO");
        }
        Vars.setStr(execution, "estadoAviso", estado);
        if ("ENTREGADO".equals(estado)) {
            Vars.setStr(execution, "medioNotificacionEfectivo", "AVISO");
            Vars.setStr(execution, "fechaEntregaAviso", Fechas.ahora());
        }
        expediente.registrar(execution, "AVISO_ENVIADO", "Notificar por aviso con copia del acto",
                "operador de notificación",
                datos("estadoAviso", estado, "direccion", Vars.obj(execution, "direccionNotificacion")));
        Vars.log("notificar-por-aviso", execution, "aviso " + estado);
    }
}
