package com.soft1.c4;

import com.soft1.common.Fechas;
import com.soft1.common.CoreServicesClient;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * C4 · Service Task "Liquidar factura" (motor de liquidación). Antes: topic "liquidar-factura".
 */
@Component("liquidarFactura")
public class LiquidarFactura implements JavaDelegate {

    private final CoreServicesClient serviciosCore;

    public LiquidarFactura(CoreServicesClient serviciosCore) {
        this.serviciosCore = serviciosCore;
    }

    @Override
    public void execute(DelegateExecution execution) {
        double cu = Vars.dbl(execution, "cuKwh", 0);
        double consumo = Vars.dbl(execution, "consumoFacturableKwh", 0);
        double factor = Vars.dbl(execution, "factorSubsidioContribucion", 0);
        double subsistencia = Vars.dbl(execution, "consumoSubsistenciaKwh", 0);

        CoreServicesClient.Liquidation liquidacion = serviciosCore.liquidar(cu, consumo, factor, subsistencia);

        Vars.setDbl(execution, "valorEnergia", liquidacion.valorEnergia());
        Vars.setDbl(execution, "valorAjusteTarifario", liquidacion.valorAjusteTarifario());
        Vars.setDbl(execution, "valorTotal", liquidacion.valorTotal());
        Vars.setStr(execution, "fechaLiquidacion", liquidacion.fechaLiquidacion());
        Vars.log("liquidar-factura", execution,
                consumo + " kWh × " + cu + " = " + liquidacion.valorEnergia() + "; ajuste "
                        + liquidacion.valorAjusteTarifario() + "; total " + liquidacion.valorTotal() + " COP");
    }
}
