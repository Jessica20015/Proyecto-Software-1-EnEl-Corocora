package com.soft1.c4;

import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * C4 · Task listener "complete" de las tareas de usuario de C4 y C4.1.
 * <p>
 * Repite en el servidor las reglas de cada Camunda Form (.form): valores permitidos, campos obligatorios,
 * longitudes mínimas y rangos. Así un dato inválido no entra al proceso aunque la tarea se complete por REST
 * o desde una interfaz que no aplique las validaciones del formulario. Si algo falla, la tarea no se completa
 * y quien la envió recibe la lista de errores.
 */
@Component("validarFormularioC4")
public class ValidarFormularioC4 implements TaskListener {

    private static final Set<String> DECISIONES_ANALISTA = Set.of("FACTURAR", "DEVOLVER_C3");
    private static final Set<String> RESULTADOS_VERIFICACION = Set.of("APROBADA", "CORREGIR");
    private static final Set<String> MOTIVOS_CORRECCION =
            Set.of("ERROR_LIQUIDACION", "ERROR_DATOS_SUMINISTRO", "ERROR_REGLA_TARIFARIA");
    private static final Set<String> ACCIONES_ENTREGA_FALLIDA =
            Set.of("ENTREGA_EN_OFICINA", "ENTREGA_PERSONAL_CON_ACTA", "PUBLICACION_PORTAL_Y_AVISO");

    @Override
    public void notify(DelegateTask tarea) {
        ValidacionC4 v = new ValidacionC4(tarea, "«" + tarea.getName() + "»");
        switch (tarea.getTaskDefinitionKey()) {
            case "Activity_RevisarExcepcion" -> v   // RevisarExcepcionConsumo.form
                    .opcion("decisionAnalista", DECISIONES_ANALISTA, true)
                    .texto("evidenciaAnalista", 20);
            case "Activity_VerificarCalidad" -> {   // VerificarCalidadFactura.form
                v.opcion("resultadoVerificacion", RESULTADOS_VERIFICACION, true);
                // el motivo solo se pide (y solo es visible en el formulario) cuando se ordena corregir
                if ("CORREGIR".equals(tarea.getVariable("resultadoVerificacion"))) {
                    v.opcion("motivoCorreccion", MOTIVOS_CORRECCION, true);
                }
            }
            case "Activity_CorregirLiquidacion" -> v   // CorregirLiquidacion.form
                    .opcion("usoServicio", ValidacionC4.USOS_SERVICIO, true)
                    .numero("estrato", 1.0, 6.0, true, true)
                    .texto("descripcionCorreccion", 15);
            case "Activity_GestionarEntregaFallida" -> v   // GestionarEntregaFallida.form (C4.1)
                    .opcion("accionEntregaFallida", ACCIONES_ENTREGA_FALLIDA, true)
                    .texto("soporteEntrega", 1);
            default -> {
                // tarea sin reglas propias
            }
        }
        v.verificar();
    }
}
