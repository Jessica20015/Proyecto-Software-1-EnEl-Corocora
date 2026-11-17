package com.soft1.c7;

import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.Instant;

import static com.soft1.c7.C7Expediente.datos;

/**
 * C7.1 (C7_NotificarDecision) · Service Task "Publicar el aviso (web y cartelera)".
 * Antes: topic "publicar-aviso".
 */
@Component("publicarAviso")
public class PublicarAviso implements JavaDelegate {

    private final C7Expediente expediente;
    private final C7Calendario calendario;

    public PublicarAviso(C7Expediente expediente, C7Calendario calendario) {
        this.expediente = expediente;
        this.calendario = calendario;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String fin = calendario.venceEnDiasHabiles(Instant.now(), 5);
        Vars.setStr(execution, "fechaFinPublicacion", fin);
        Vars.setStr(execution, "medioNotificacionEfectivo", "AVISO_PUBLICADO");
        expediente.registrar(execution, "AVISO_PUBLICADO", "Publicar el aviso (web y cartelera)", "sistema",
                datos("fechaFinPublicacion", fin));
        Vars.log("publicar-aviso", execution, "aviso publicado hasta " + fin);
    }
}
