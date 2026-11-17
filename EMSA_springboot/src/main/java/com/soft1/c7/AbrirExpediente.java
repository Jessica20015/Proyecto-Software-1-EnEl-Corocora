package com.soft1.c7;

import com.soft1.common.Fechas;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.Instant;

import static com.soft1.c7.C7Expediente.datos;

/**
 * C7 · Service Task "Abrir expediente y calcular vencimiento". Antes: topic "abrir-expediente".
 */
@Component("abrirExpediente")
public class AbrirExpediente implements JavaDelegate {

    private final C7Expediente expediente;
    private final C7Calendario calendario;

    public AbrirExpediente(C7Expediente expediente, C7Calendario calendario) {
        this.expediente = expediente;
        this.calendario = calendario;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Instant desde = Fechas.parse(Vars.str(execution, "fechaRadicacion"));
        Object terminoOriginal = Vars.tiene(execution, "terminoDiasHabiles") ? Vars.obj(execution, "terminoDiasHabiles") : 15;
        double termino = Vars.dbl(execution, "terminoDiasHabiles", 15);
        String vence = calendario.venceEnDiasHabiles(desde, termino);
        String alerta = calendario.alertaAl70(desde, termino);

        Vars.setStr(execution, "fechaVencimiento", vence);
        Vars.setStr(execution, "vencimientoLegible", calendario.legible(vence));
        Vars.setStr(execution, "fechaAlerta", alerta);

        expediente.registrar(execution,
                "PRIMERA".equals(Vars.str(execution, "instancia")) ? "EXPEDIENTE_ABIERTO" : "RECURSO_ACUMULADO",
                "Abrir expediente y calcular vencimiento", "sistema",
                datos("estado", "EN_TRAMITE", "terminoDiasHabiles", terminoOriginal,
                        "fechaVencimiento", vence, "fechaAlerta", alerta,
                        "procesoEvidencia", Vars.obj(execution, "procesoEvidencia"),
                        "prioridad", Vars.obj(execution, "prioridad"),
                        "alertaProcedencia", Vars.str(execution, "alertaProcedencia", "")));
        Vars.log("abrir-expediente", execution,
                "término " + terminoOriginal + " días hábiles → vence " + vence + " (alerta " + alerta + ")");
    }
}
