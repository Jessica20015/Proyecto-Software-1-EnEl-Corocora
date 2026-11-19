package com.soft1.c7;

import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * C7 · Task listener "complete" de las tareas de usuario de C7 y C7.1.
 * <p>
 * Repite en el servidor las reglas de cada Camunda Form (.form): valores permitidos, campos obligatorios,
 * longitudes mínimas, números y casillas. Así un dato inválido no entra al proceso aunque la tarea se complete
 * por REST o desde una interfaz que no aplique las validaciones del formulario. Si algo falla, la tarea no se
 * completa y quien la envió recibe la lista de errores.
 */
@Component("validarFormularioC7")
public class ValidarFormularioC7 implements TaskListener {

    static final Set<String> TIPOS_PQR = Set.of("PETICION", "QUEJA", "RECLAMO", "RECURSO_REPOSICION");
    /** TipificarEnrutarPQR no ofrece «OTRO»: el orientador debe elegir un asunto concreto. */
    private static final Set<String> ASUNTOS_TIPIFICADOS = Set.of("FACTURACION", "MEDICION", "SUSPENSION_RECONEXION",
            "RECUPERACION_CONSUMOS", "CALIDAD_CONTINUIDAD", "ATENCION_SERVICIO");
    private static final Set<String> SENTIDOS = Set.of("FAVORABLE", "PARCIAL", "DESFAVORABLE");
    private static final Set<String> CAUSAS_RAIZ = Set.of("ERROR_LECTURA", "ESTIMACION", "ERROR_LIQUIDACION",
            "DESVIACION_NO_INVESTIGADA", "ACTUACION_SIN_SOPORTE", "SUSPENSION_INDEBIDA", "CALIDAD_SERVICIO",
            "ATENCION_DEFICIENTE", "SIN_FALLA_EMPRESA");
    /** ReconocerSilencioPositivo agrega la causa «vencimiento del término». */
    private static final Set<String> CAUSAS_RAIZ_SILENCIO;
    static {
        java.util.HashSet<String> causas = new java.util.HashSet<>(CAUSAS_RAIZ);
        causas.add("VENCIMIENTO_TERMINO");
        CAUSAS_RAIZ_SILENCIO = Set.copyOf(causas);
    }
    private static final Set<String> RESULTADOS_REVISION = Set.of("APROBAR", "DEVOLVER");

    @Override
    public void notify(DelegateTask tarea) {
        ValidacionC7 v = new ValidacionC7(tarea, "«" + tarea.getName() + "»");
        switch (tarea.getTaskDefinitionKey()) {
            case "Activity_TipificarManual" -> v   // TipificarEnrutarPQR.form
                    .opcion("tipoPQR", TIPOS_PQR, true)
                    .opcion("asunto", ASUNTOS_TIPIFICADOS, true)
                    .texto("justificacionTipificacion", 15);
            case "Activity_ProyectarRespuesta" -> v   // ProyectarRespuestaPQR.form
                    .opcion("sentidoDecision", SENTIDOS, true)
                    .texto("motivacion", 40)
                    .booleano("ordenaAjusteC4", false, false)
                    .numero("valorAjuste", 0, false)
                    .booleano("solicitaRevisionC6", false, false)
                    .opcion("causaRaiz", CAUSAS_RAIZ, true);
            case "Activity_RevisarJuridica" -> v   // RevisarProyectoRespuesta.form
                    .opcion("resultadoRevision", RESULTADOS_REVISION, true)
                    .texto("observacionesRevision", 10);
            case "Activity_ReconocerSilencio" -> v   // ReconocerSilencioPositivo.form
                    .texto("motivacion", 20)
                    .booleano("ordenaAjusteC4", false, false)
                    .numero("valorAjuste", 0, false)
                    .booleano("solicitaRevisionC6", false, false)
                    .opcion("causaRaiz", CAUSAS_RAIZ_SILENCIO, true);
            case "Activity_NotificarPersonalmente" -> v   // NotificarPersonalmente.form (C7.1)
                    .booleano("identidadVerificada", true, true)
                    .booleano("copiaEntregada", true, true)
                    .booleano("recursosInformados", true, true);
            default -> {
                // tarea sin reglas propias
            }
        }
        v.verificar();
    }
}
