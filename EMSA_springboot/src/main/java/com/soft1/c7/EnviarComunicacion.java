package com.soft1.c7;

import com.soft1.common.Fechas;
import com.soft1.common.MensajeriaSimulada;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.variable.Variables;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static com.soft1.c7.C7Expediente.datos;

/**
 * C7.1 (C7_NotificarDecision) · Evento de mensaje (envío) "Notificación electrónica o citación enviada"
 * (MsgComunicacionDecision). Antes: topic "enviar-comunicacion".
 * Simula al usuario y al operador: acceso electrónico o comparecencia, según simularNotificacion.
 */
@Component("enviarComunicacion")
public class EnviarComunicacion implements JavaDelegate {

    private final C7Expediente expediente;
    private final C7Calendario calendario;
    private final MensajeriaSimulada mensajeria;

    public EnviarComunicacion(C7Expediente expediente, C7Calendario calendario, MensajeriaSimulada mensajeria) {
        this.expediente = expediente;
        this.calendario = calendario;
        this.mensajeria = mensajeria;
    }

    @Override
    public void execute(DelegateExecution execution) {
        boolean electronica = Vars.bool(execution, "autorizaNotificacionElectronica", false);
        String limite = calendario.venceEnDiasHabiles(Instant.now(), 5);
        Vars.setStr(execution, "medioComunicacion", electronica ? "ELECTRONICA" : "CITACION");
        Vars.setStr(execution, "fechaLimiteNotificacion", limite);

        expediente.registrar(execution, "COMUNICACION_ENVIADA", "Notificación electrónica o citación enviada",
                "operador de notificación",
                datos("medio", electronica ? "ELECTRONICA" : "CITACION",
                        "destino", electronica ? Vars.obj(execution, "correoNotificacion") : Vars.obj(execution, "direccionNotificacion"),
                        "fechaLimiteNotificacion", limite));
        Vars.log("enviar-comunicacion", execution, (electronica ? "notificación electrónica" : "citación para notificación personal")
                + " · plazo " + limite);

        // simulación del usuario y del operador
        boolean reposicion = "REPOSICION".equals(Vars.str(execution, "instancia"));
        String sim = reposicion ? Vars.str(execution, "simularNotificacionReposicion") : null;
        if (sim == null) {
            sim = Vars.str(execution, "simularNotificacion", electronica ? "ACCESO_ELECTRONICO" : "COMPARECE");
        }
        String instancia = execution.getProcessInstanceId();
        Map<String, Object> variables = new HashMap<>();
        if ("ACCESO_ELECTRONICO".equals(sim)) {
            variables.put("fechaAccesoElectronico", Variables.stringValue(Fechas.ahora()));
            mensajeria.porInstancia(3000, "MsgAccesoElectronico", instancia, variables);
        } else if ("COMPARECE".equals(sim)) {
            variables.put("fechaComparecencia", Variables.stringValue(Fechas.ahora()));
            mensajeria.porInstancia(3000, "MsgComparecenciaNotificacion", instancia, variables);
        } else {
            Vars.log("enviar-comunicacion", execution,
                    "(simulación: el usuario no se notifica; vencerá el plazo y se notificará por aviso)");
        }
    }
}
