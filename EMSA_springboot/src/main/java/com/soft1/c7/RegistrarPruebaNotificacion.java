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

import static com.soft1.c7.C7Expediente.datos;

/**
 * C7.1 (C7_NotificarDecision) · Service Task "Registrar la prueba de notificación".
 * Antes: topic "registrar-prueba-notificacion".
 * Simula además al usuario que recurre (solo contra la decisión de primera instancia), según simularRecurso:
 * NINGUNO | REPOSICION | REPOSICION_Y_APELACION.
 */
@Component("registrarPruebaNotificacion")
public class RegistrarPruebaNotificacion implements JavaDelegate {

    private final C7Expediente expediente;
    private final C7Calendario calendario;
    private final MensajeriaSimulada mensajeria;

    public RegistrarPruebaNotificacion(C7Expediente expediente, C7Calendario calendario, MensajeriaSimulada mensajeria) {
        this.expediente = expediente;
        this.calendario = calendario;
        this.mensajeria = mensajeria;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String medio = Vars.str(execution, "medioNotificacionEfectivo", "PERSONAL");
        Instant fechaNotificacion = Instant.now();
        // por aviso, la notificación se entiende surtida al finalizar el día siguiente a la entrega (art. 69 CPACA)
        if ("AVISO".equals(medio) && calendario.esProduccion()) {
            fechaNotificacion = Fechas.parse(calendario.venceEnDiasHabiles(Instant.now(), 1));
        }
        // si operó el silencio administrativo positivo, por definición la respuesta no se dio dentro del término
        String vencimiento = Vars.str(execution, "fechaVencimiento");
        boolean enTermino = !Vars.bool(execution, "silencioPositivo", false)
                && vencimiento != null
                && !fechaNotificacion.isAfter(Fechas.parse(vencimiento));
        boolean procede = Vars.bool(execution, "procedenRecursos", false);
        String limiteRecursos = procede
                ? calendario.venceEnDiasHabiles(fechaNotificacion, Vars.dbl(execution, "plazoRecursosDiasHabiles", 5))
                : "";

        Vars.setStr(execution, "fechaNotificacion", Fechas.iso(fechaNotificacion));
        Vars.setBool(execution, "notificadaEnTermino", enTermino);
        Vars.setStr(execution, "fechaLimiteRecursos", limiteRecursos);

        expediente.registrar(execution, "NOTIFICADA", "Registrar la prueba de notificación", "sistema",
                datos("estado", "NOTIFICADA", "numeroActo", Vars.obj(execution, "numeroActo"),
                        "medio", medio, "fechaNotificacion", Fechas.iso(fechaNotificacion),
                        "notificadaEnTermino", enTermino, "fechaLimiteRecursos", limiteRecursos));
        Vars.log("registrar-prueba-notificacion", execution, "notificada por " + medio + " · en término: " + enTermino
                + (procede ? " · recursos hasta " + limiteRecursos : ""));

        // simulación del usuario que recurre (solo contra la decisión de primera instancia)
        String recurso = Vars.str(execution, "simularRecurso", "NINGUNO");
        if ("PRIMERA".equals(Vars.str(execution, "instancia")) && procede && !"NINGUNO".equals(recurso)) {
            String expedienteId = Vars.str(execution, "expedienteId", execution.getProcessBusinessKey());
            Map<String, Object> variables = new HashMap<>();
            variables.put("apelacionSubsidiaria", Variables.booleanValue("REPOSICION_Y_APELACION".equals(recurso)));
            variables.put("argumentosRecurso", Variables.stringValue(
                    "El usuario no está de acuerdo con la decisión y aporta nuevos argumentos."));
            mensajeria.porBusinessKey(6000, "MsgRecursoReposicion", expedienteId, variables);
            Vars.log("registrar-prueba-notificacion", execution, "(simulación: el usuario presentará "
                    + ("REPOSICION_Y_APELACION".equals(recurso) ? "reposición y apelación en subsidio" : "reposición") + ")");
        }
    }
}
