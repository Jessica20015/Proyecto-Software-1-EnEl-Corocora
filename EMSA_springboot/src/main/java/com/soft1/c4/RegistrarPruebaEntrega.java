package com.soft1.c4;

import com.soft1.common.DespuesDelCommit;
import com.soft1.common.Fechas;
import com.soft1.common.JsonlStore;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C4.1 (C4_EntregarFactura) · Service Task "Registrar prueba de entrega". Antes: topic "registrar-prueba-entrega".
 * <p>
 * Última actividad del perfil de C4: deja la prueba de entrega (fecha-hora, canal, soporte y estado) en el
 * expediente de facturación, junto al registro de EMISION, y cierra el indicador "ciclo de facturación".
 */
@Component("registrarPruebaEntrega")
public class RegistrarPruebaEntrega implements JavaDelegate {

    private static final List<String> CAMPOS = List.of(
            "codigoSuministro", "periodo", "numeroFactura", "versionFactura", "cuentaPorCobrarId", "canalEntrega",
            "intentosEntrega", "estadoEntrega", "soporteEntrega", "accionEntregaFallida", "observacionEntrega",
            "fechaLiberacionC3", "fechaEmision", "fechaEntrega", "cicloFacturacionHoras");

    private final JsonlStore store;

    public RegistrarPruebaEntrega(JsonlStore store) {
        this.store = store;
    }

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

        Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("tipoRegistro", "ENTREGA");
        registro.put("registradoEn", Fechas.ahora());
        registro.put("processInstanceId", execution.getProcessInstanceId());
        registro.put("businessKey", execution.getProcessBusinessKey());
        for (String campo : CAMPOS) {
            registro.put(campo, Vars.obj(execution, campo));
        }
        // como en RegistrarExpediente: se escribe solo si la transacción del engine se confirma
        DespuesDelCommit.ejecutar(() -> store.agregar(RegistrarExpediente.ARCHIVO, registro));

        Vars.log("registrar-prueba-entrega", execution, "prueba registrada ("
                + Vars.str(execution, "soporteEntrega") + "); ciclo de facturación " + horas + " h");
    }
}
