package com.soft1.c2;

import com.soft1.common.Vars;
import com.soft1.common.CoreServicesClient;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * C2 · Service Task "Solicitar equipos" (SolicitarEquipos).
 * Antes: worker de Node.js con topic "solicitud_repuestos_topic".
 */
@Component("solicitudRepuestos")
public class SolicitudRepuestos implements JavaDelegate {

    private final CoreServicesClient serviciosCore;

    public SolicitudRepuestos(CoreServicesClient serviciosCore) {
        this.serviciosCore = serviciosCore;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String nivelDano = Vars.str(execution, "nivelDano");
        String domicilio = Vars.str(execution, "domicilio");
        String codigoSolicitud = execution.getProcessBusinessKey();
        if (codigoSolicitud == null || codigoSolicitud.isBlank()) {
            codigoSolicitud = execution.getProcessInstanceId();
        }
        CoreServicesClient.TechnicalOrder orden = serviciosCore.registrarOrden(codigoSolicitud,
                Vars.str(execution, "codigoSuministro", ""), nivelDano);
        Vars.log("solicitud_repuestos", execution,
                "Orden " + orden.id() + " para daño " + nivelDano + " en " + domicilio + ".");

        Vars.setBool(execution, "equiposSolicitados", true);
        Vars.setStr(execution, "numeroSolicitudEquipos", orden.id());
    }
}
