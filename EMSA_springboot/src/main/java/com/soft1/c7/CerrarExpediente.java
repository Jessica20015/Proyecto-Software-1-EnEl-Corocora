package com.soft1.c7;

import com.soft1.common.Fechas;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.Instant;

import static com.soft1.c7.C7Expediente.datos;

/**
 * C7 · Service Task "Cerrar o remitir el expediente". Antes: topic "cerrar-expediente".
 */
@Component("cerrarExpediente")
public class CerrarExpediente implements JavaDelegate {

    private final C7Expediente expediente;
    private final C7Calendario calendario;

    public CerrarExpediente(C7Expediente expediente, C7Calendario calendario) {
        this.expediente = expediente;
        this.calendario = calendario;
    }

    @Override
    public void execute(DelegateExecution execution) {
        boolean remitido = Vars.tiene(execution, "fechaRemisionSSPD");
        String estado = remitido ? "REMITIDO_SSPD" : "CERRADO";
        Instant radicacion = Fechas.parse(Vars.str(execution, "fechaRadicacion"));
        Instant notificacion = Vars.tiene(execution, "fechaNotificacion")
                ? Fechas.parse(Vars.str(execution, "fechaNotificacion")) : Instant.now();
        double dias = calendario.diasHabilesEntre(radicacion, notificacion);

        Vars.setStr(execution, "estadoExpediente", estado);
        Vars.setStr(execution, "fechaCierre", Fechas.ahora());
        Vars.setDbl(execution, "diasHabilesRespuesta", dias);

        expediente.registrar(execution, "EXPEDIENTE_CERRADO", "Cerrar o remitir el expediente", "sistema",
                datos("estado", estado,
                        "causa", Vars.obj(execution, "causaRaiz"),
                        "codigoSuministro", Vars.obj(execution, "codigoSuministro"),
                        "asunto", Vars.obj(execution, "asunto"),
                        "tipoPQR", Vars.obj(execution, "tipoPQR"),
                        "sentidoDecision", Vars.obj(execution, "sentidoDecision"),
                        "notificadaEnTermino", Vars.obj(execution, "notificadaEnTermino"),
                        "diasHabilesRespuesta", dias,
                        "silencioPositivo", Vars.bool(execution, "silencioPositivo", false),
                        "esReapertura", Vars.bool(execution, "esReapertura", false),
                        "radicadoAnterior", Vars.str(execution, "radicadoAnterior", ""),
                        "medioNotificacion", Vars.obj(execution, "medioNotificacionEfectivo"),
                        "alertaTerminoEmitida", Vars.bool(execution, "alertaTerminoEmitida", false)));
        Vars.log("cerrar-expediente", execution, estado + " · notificada en término: "
                + Vars.str(execution, "notificadaEnTermino") + " · " + dias + " días hábiles");
    }
}
