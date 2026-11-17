package com.soft1.c4;

import com.soft1.common.Fechas;
import com.soft1.common.JsonlStore;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C4 · Service Task "Registrar expediente de trazabilidad". Antes: topic "registrar-expediente".
 * Data store "Expediente de facturación": un archivo JSONL (evidencia para C7).
 */
@Component("registrarExpediente")
public class RegistrarExpediente implements JavaDelegate {

    static final String ARCHIVO = "expediente-facturacion.jsonl";

    private static final List<String> CAMPOS = List.of(
            "codigoSuministro", "periodo", "esRefacturacion", "origenRefacturacion", "tipoLectura",
            "consumoKwh", "consumoPromedioKwh", "desviacionAbsPct", "decisionConsumo", "motivoExcepcion",
            "decisionAnalista", "evidenciaAnalista", "devolucionesC3", "facturaProvisional", "consumoFacturableKwh",
            "usoServicio", "estrato", "versionRegla", "cuKwh", "factorSubsidioContribucion", "valorEnergia",
            "valorAjusteTarifario", "valorTotal", "correccionesPreEmision", "motivoCorreccion", "descripcionCorreccion",
            "numeroFactura", "versionFactura", "cuentaPorCobrarId", "fechaLiberacionC3", "fechaEmision");

    private final JsonlStore store;

    public RegistrarExpediente(JsonlStore store) {
        this.store = store;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("registradoEn", Fechas.ahora());
        registro.put("processInstanceId", execution.getProcessInstanceId());
        registro.put("businessKey", execution.getProcessBusinessKey());
        for (String campo : CAMPOS) {
            registro.put(campo, Vars.obj(execution, campo));
        }
        store.agregar(ARCHIVO, registro);

        String ruta = store.ruta(ARCHIVO).toAbsolutePath().toString();
        Vars.setStr(execution, "expedienteRegistrado", ruta);
        Vars.log("registrar-expediente", execution, "expediente guardado en " + ruta);
    }
}
