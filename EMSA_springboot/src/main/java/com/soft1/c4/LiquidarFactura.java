package com.soft1.c4;

import com.soft1.common.Fechas;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * C4 · Service Task "Liquidar factura" (motor de liquidación). Antes: topic "liquidar-factura".
 */
@Component("liquidarFactura")
public class LiquidarFactura implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        double cu = Vars.dbl(execution, "cuKwh", 0);
        double consumo = Vars.dbl(execution, "consumoFacturableKwh", 0);
        double factor = Vars.dbl(execution, "factorSubsidioContribucion", 0);
        double subsistencia = Vars.dbl(execution, "consumoSubsistenciaKwh", 0);

        long valorEnergia = Math.round(cu * consumo);
        long ajuste = 0;
        if (factor < 0) {
            ajuste = Math.round(factor * cu * Math.min(consumo, subsistencia)); // subsidio: solo subsistencia
        } else if (factor > 0) {
            ajuste = Math.round(factor * cu * consumo);                         // contribución: todo el consumo
        }
        long total = valorEnergia + ajuste;

        Vars.setDbl(execution, "valorEnergia", valorEnergia);
        Vars.setDbl(execution, "valorAjusteTarifario", ajuste);
        Vars.setDbl(execution, "valorTotal", total);
        Vars.setStr(execution, "fechaLiquidacion", Fechas.ahora());
        Vars.log("liquidar-factura", execution,
                consumo + " kWh × " + cu + " = " + valorEnergia + "; ajuste " + ajuste + "; total " + total + " COP");
    }
}
