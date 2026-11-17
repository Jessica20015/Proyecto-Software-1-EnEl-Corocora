package com.soft1.c7;

import com.soft1.common.Fechas;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import static com.soft1.c7.C7Expediente.datos;

/**
 * C7 · Service Task "Escalar al dueño del proceso fuente". Antes: topic "escalar-evidencia".
 */
@Component("escalarEvidencia")
public class EscalarEvidencia implements JavaDelegate {

    private final C7Expediente expediente;

    public EscalarEvidencia(C7Expediente expediente) {
        this.expediente = expediente;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String texto = "Sin respuesta de " + Vars.str(execution, "procesoEvidencia")
                + " dentro del plazo interno. Se escaló al dueño del proceso el " + Fechas.ahora()
                + ". El analista decide con la evidencia disponible.";
        Vars.setBool(execution, "evidenciaOportuna", false);
        Vars.setStr(execution, "evidenciaRecibida", texto);
        expediente.registrar(execution, "EVIDENCIA_ESCALADA", "Escalar al dueño del proceso fuente", "sistema",
                datos("procesoEvidencia", Vars.obj(execution, "procesoEvidencia")));
        Vars.log("escalar-evidencia", execution, texto);
    }
}
