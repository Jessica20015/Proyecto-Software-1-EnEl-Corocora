package com.soft1.c7;

import com.soft1.common.Fechas;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import static com.soft1.c7.C7Expediente.datos;

/**
 * C7.1 (C7_NotificarDecision) · Service Task "Generar el acto de respuesta motivado".
 * Antes: topic "generar-acto-respuesta".
 */
@Component("generarActoRespuesta")
public class GenerarActoRespuesta implements JavaDelegate {

    private final C7Expediente expediente;

    public GenerarActoRespuesta(C7Expediente expediente) {
        this.expediente = expediente;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String sufijo = "REPOSICION".equals(Vars.str(execution, "instancia")) ? "R" : "P";
        String numeroActo = "ACT-" + Vars.str(execution, "radicado") + "-" + sufijo;
        Vars.setStr(execution, "numeroActo", numeroActo);
        Vars.setStr(execution, "fechaActo", Fechas.ahora());

        expediente.registrar(execution, "ACTO_GENERADO", "Generar el acto de respuesta motivado", "sistema",
                datos("numeroActo", numeroActo,
                        "sentidoDecision", Vars.obj(execution, "sentidoDecision"),
                        "silencioPositivo", Vars.bool(execution, "silencioPositivo", false),
                        "motivacion", Vars.obj(execution, "motivacion"),
                        "textoRecursos", Vars.obj(execution, "textoRecursos")));
        Vars.log("generar-acto-respuesta", execution, numeroActo + " · " + Vars.str(execution, "sentidoDecision")
                + " · recursos: " + Vars.str(execution, "procedenRecursos"));
    }
}
