package com.soft1.c4;

import com.soft1.common.DespuesDelCommit;
import com.soft1.common.Fechas;
import com.soft1.common.JsonlStore;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * C4 · Service Task "Registrar expediente de trazabilidad". Antes: topic "registrar-expediente".
 * Data store "Expediente de facturación": un archivo JSONL (evidencia para C7).
 * <p>
 * Escribe el registro de EMISION; el de ENTREGA lo agrega {@link RegistrarPruebaEntrega} en C4.1.
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

    /**
     * Solo tienen sentido si hubo al menos una corrección antes de emitir. Un formulario que no respete el
     * conditional.hide de VerificarCalidadFactura puede enviar el motivo por defecto aunque la factura se apruebe:
     * no debe quedar como si la factura se hubiera corregido (indicador "factura correcta a la primera").
     */
    private static final Set<String> CAMPOS_DE_CORRECCION = Set.of("motivoCorreccion", "descripcionCorreccion");

    private final JsonlStore store;

    public RegistrarExpediente(JsonlStore store) {
        this.store = store;
    }

    @Override
    public void execute(DelegateExecution execution) {
        boolean huboCorreccion = Vars.entero(execution, "correccionesPreEmision", 0) > 0;

        Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("tipoRegistro", "EMISION");
        registro.put("registradoEn", Fechas.ahora());
        registro.put("processInstanceId", execution.getProcessInstanceId());
        registro.put("businessKey", execution.getProcessBusinessKey());
        for (String campo : CAMPOS) {
            boolean omitir = !huboCorreccion && CAMPOS_DE_CORRECCION.contains(campo);
            registro.put(campo, omitir ? null : Vars.obj(execution, campo));
        }
        // el registro se arma ya, pero se escribe al confirmar la transacción: si el engine la revierte
        // (falla el envío, la notificación a C5 o el servicio Core), no queda un registro fantasma ni duplicado
        DespuesDelCommit.ejecutar(() -> store.agregar(ARCHIVO, registro));

        String ruta = store.ruta(ARCHIVO).toAbsolutePath().toString();
        Vars.setStr(execution, "expedienteRegistrado", ruta);
        Vars.log("registrar-expediente", execution, "expediente (emisión) guardado en " + ruta);
    }
}
