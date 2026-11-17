package com.soft1.c4;

import com.soft1.common.Fechas;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * C4 · Service Task "Emitir factura". Antes: topic "emitir-factura".
 */
@Component("emitirFactura")
public class EmitirFactura implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String codigo = Vars.str(execution, "codigoSuministro", "SIN-CODIGO");
        String periodo = Vars.str(execution, "periodo", "0000-00");
        boolean esRefacturacion = Vars.bool(execution, "esRefacturacion", false);
        int version = esRefacturacion ? Vars.entero(execution, "versionFacturaAnterior", 1) + 1 : 1;
        Instant emision = Instant.now();
        Instant limite = emision.plus(Duration.ofDays(15));

        String numeroFactura = "FE-" + periodo.replace("-", "") + "-" + codigo + "-v" + version;
        Vars.setInt(execution, "versionFactura", version);
        Vars.setStr(execution, "numeroFactura", numeroFactura);
        Vars.setStr(execution, "cuentaPorCobrarId", "CXC-" + codigo + "-" + periodo); // una sola cuenta por suministro-período
        Vars.setStr(execution, "fechaEmision", Fechas.iso(emision));
        Vars.setStr(execution, "fechaLimitePago", Fechas.iso(limite).substring(0, 10));
        Vars.log("emitir-factura", execution, "factura " + numeroFactura + " emitida");
    }
}
