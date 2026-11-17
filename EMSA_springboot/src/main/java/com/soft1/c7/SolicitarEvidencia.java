package com.soft1.c7;

import com.soft1.common.Fechas;
import com.soft1.common.MensajeriaSimulada;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.variable.Variables;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.soft1.c7.C7Expediente.datos;

/**
 * C7 · Evento intermedio de mensaje (envío) "Evidencia solicitada al proceso fuente". Antes: topic "solicitar-evidencia".
 * Simula al proceso fuente (C2–C6): si simularEvidencia = RESPONDE (por defecto) responde con evidencia ficticia.
 */
@Component("solicitarEvidencia")
public class SolicitarEvidencia implements JavaDelegate {

    /** Evidencia ficticia por proceso fuente (sin datos personales reales). */
    private static final Map<String, Function<DelegateExecution, String>> EVIDENCIA = Map.of(
            "C2", e -> "C2 · Evento de interrupción en el circuito que alimenta " + Vars.str(e, "codigoSuministro")
                    + ": 7,5 h acumuladas en el período (DIU), causa validada «falla en transformador». Afectación documentada.",
            "C3", e -> "C3 · Histórico de lecturas de " + Vars.str(e, "codigoSuministro")
                    + ": 6 lecturas reales consecutivas; la del período reclamado fue estimada por imposibilidad de acceso. Secuencialidad verificada.",
            "C4", e -> "C4 · Trazabilidad de la factura " + Vars.str(e, "numeroFactura", "del período")
                    + ": lectura de C3, regla tarifaria CU-2026-10 v1 aprobada por G3 y liquidación sin corrección previa. Expediente de facturación disponible.",
            "C5", e -> "C5 · Cuenta por cobrar de " + Vars.str(e, "codigoSuministro")
                    + ": suspensión ejecutada por mora de 2 facturas; pago registrado antes de la suspensión, no aplicado a tiempo.",
            "C6", e -> "C6 · Actuación de recuperación sobre " + Vars.str(e, "codigoSuministro")
                    + ": acta de revisión con anomalía, período de recuperación de 6 meses, sin secuencialidad de lecturas en 2 de ellos.");

    private final C7Expediente expediente;
    private final C7Calendario calendario;
    private final MensajeriaSimulada mensajeria;

    public SolicitarEvidencia(C7Expediente expediente, C7Calendario calendario, MensajeriaSimulada mensajeria) {
        this.expediente = expediente;
        this.calendario = calendario;
        this.mensajeria = mensajeria;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String proceso = Vars.str(execution, "procesoEvidencia");
        String limite = calendario.venceEnDiasHabiles(Instant.now(), Vars.dbl(execution, "plazoEvidenciaDiasHabiles", 2));
        Vars.setStr(execution, "fechaLimiteEvidencia", limite);

        expediente.registrar(execution, "EVIDENCIA_SOLICITADA", "Evidencia solicitada al proceso fuente", "sistema",
                datos("procesoEvidencia", proceso, "fechaLimiteEvidencia", limite));
        Vars.log("solicitar-evidencia", execution, "solicitud a " + proceso + ", plazo interno " + limite);

        // simulación del proceso fuente
        if ("RESPONDE".equals(Vars.str(execution, "simularEvidencia", "RESPONDE"))) {
            String texto = EVIDENCIA.getOrDefault(proceso, e -> proceso + ": evidencia del asunto.").apply(execution);
            Map<String, Object> variables = new HashMap<>();
            variables.put("evidenciaRecibida", Variables.stringValue(texto));
            variables.put("fechaEvidencia", Variables.stringValue(Fechas.ahora()));
            mensajeria.porInstancia(3000, "MsgEvidenciaPQR", execution.getProcessInstanceId(), variables);
        } else {
            Vars.log("solicitar-evidencia", execution,
                    "(simulación: " + proceso + " no responde; vencerá el plazo interno)");
        }
    }
}
