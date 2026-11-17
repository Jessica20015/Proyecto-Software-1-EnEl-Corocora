package com.soft1.c4;

import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * C4 · Service Task "Asignar consumo promedio (art. 149 Ley 142)". Antes: topic "asignar-consumo-promedio".
 */
@Component("asignarConsumoPromedio")
public class AsignarConsumoPromedio implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        double promedio = Vars.dbl(execution, "consumoPromedioKwh", 0);
        Vars.setDbl(execution, "consumoFacturableKwh", promedio);
        Vars.setBool(execution, "facturaProvisional", true);
        Vars.setStr(execution, "motivoProvisional",
                "C3 no respondió dentro del plazo: se factura por promedio mientras se establece la causa (art. 149, Ley 142 de 1994)");
        Vars.log("asignar-consumo-promedio", execution, "factura provisional con " + promedio + " kWh");
    }
}
