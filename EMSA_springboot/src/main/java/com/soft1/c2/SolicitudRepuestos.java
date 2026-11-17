package com.soft1.c2;

import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * C2 · Service Task "Solicitar equipos" (SolicitarEquipos).
 * Antes: worker de Node.js con topic "solicitud_repuestos_topic".
 */
@Component("solicitudRepuestos")
public class SolicitudRepuestos implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String nivelDano = Vars.str(execution, "nivelDano");
        String domicilio = Vars.str(execution, "domicilio");
        Vars.log("solicitud_repuestos", execution,
                "Daño " + nivelDano + " en " + domicilio + ": solicitando equipos...");

        Vars.setBool(execution, "equiposSolicitados", true);
        Vars.setStr(execution, "numeroSolicitudEquipos", "SOL-" + System.currentTimeMillis());
    }
}
