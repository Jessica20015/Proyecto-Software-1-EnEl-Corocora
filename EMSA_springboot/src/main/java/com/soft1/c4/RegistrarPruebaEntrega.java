package com.soft1.c4;

import com.soft1.common.Fechas;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * C4.1 (C4_EntregarFactura) · Service Task "Registrar prueba de entrega". Antes: topic "registrar-prueba-entrega".
 */
@Component("registrarPruebaEntrega")
public class RegistrarPruebaEntrega implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        Instant entrega = Instant.now();
        String liberacion = Vars.str(execution, "fechaLiberacionC3");
        Double horas = null;
        if (liberacion != null) {
            long ms = entrega.toEpochMilli() - Fechas.parse(liberacion).toEpochMilli();
            horas = Math.round(ms / 36000.0) / 100.0; // horas con 2 decimales
        }
        String alterno = Vars.str(execution, "accionEntregaFallida");

        Vars.setStr(execution, "fechaEntrega", Fechas.iso(entrega));
        Vars.setStr(execution, "estadoEntrega", alterno != null ? "ENTREGADA_MEDIO_ALTERNO (" + alterno + ")" : "ENTREGADA");
        if (horas != null) {
            Vars.setDbl(execution, "cicloFacturacionHoras", horas);
        }
        Vars.log("registrar-prueba-entrega", execution, "prueba registrada ("
                + Vars.str(execution, "soporteEntrega") + "); ciclo de facturación " + horas + " h");
    }
}
