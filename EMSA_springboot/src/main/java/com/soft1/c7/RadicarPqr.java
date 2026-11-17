package com.soft1.c7;

import com.soft1.common.Fechas;
import com.soft1.common.Vars;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.soft1.c7.C7Expediente.datos;

/**
 * C7 · Service Task "Radicar la PQR y asignar radicado". Antes: topic "radicar-pqr".
 * (El listener de fin del modelo asigna expedienteId como clave de negocio de la instancia.)
 */
@Component("radicarPqr")
public class RadicarPqr implements JavaDelegate {

    private final C7Expediente expediente;
    private final C7Calendario calendario;

    public RadicarPqr(C7Expediente expediente, C7Calendario calendario) {
        this.expediente = expediente;
        this.calendario = calendario;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(DelegateExecution execution) {
        String tipo = Vars.str(execution, "tipoPQR");
        String instancia = "RECURSO_REPOSICION".equals(tipo) ? "REPOSICION" : "PRIMERA";
        String radicado = expediente.siguienteRadicado();
        String expedienteId = Vars.str(execution, "expedienteId", radicado);
        Instant fecha = Instant.now();

        // meses desde la expedición de la factura reclamada (art. 154, Ley 142: cinco meses)
        int meses = 0;
        String fFactura = Vars.str(execution, "fechaExpedicionFactura");
        if (fFactura != null) {
            LocalDate f = LocalDate.parse(fFactura.substring(0, 10));
            ZonedDateTime hoy = fecha.atZone(ZoneOffset.UTC);
            meses = (hoy.getYear() - f.getYear()) * 12 + (hoy.getMonthValue() - f.getMonthValue())
                    - (hoy.getDayOfMonth() < f.getDayOfMonth() ? 1 : 0);
        }

        // reapertura por la misma causa: mismo suministro y asunto, cerrado hace 30 días o menos (§8.3)
        boolean esReapertura = false;
        String radicadoAnterior = "";
        if ("PRIMERA".equals(instancia)) {
            Duration ventana = calendario.esProduccion()
                    ? Duration.ofDays(30)
                    : Duration.ofSeconds(30 * calendario.segundosPorDia());
            String suministro = Vars.str(execution, "codigoSuministro");
            String asunto = Vars.str(execution, "asunto");
            List<Map<String, Object>> cerrados = expediente.leer().stream()
                    .filter(r -> "EXPEDIENTE_CERRADO".equals(r.get("evento")))
                    .filter(r -> {
                        Map<String, Object> d = (Map<String, Object>) r.get("datos");
                        return d != null && Objects.equals(d.get("codigoSuministro"), suministro)
                                && Objects.equals(d.get("asunto"), asunto);
                    })
                    .toList();
            if (!cerrados.isEmpty()) {
                Map<String, Object> ultimo = cerrados.get(cerrados.size() - 1);
                Instant cierre = Fechas.parse(ultimo.get("fechaHora"));
                if (Duration.between(cierre, fecha).compareTo(ventana) <= 0) {
                    esReapertura = true;
                    radicadoAnterior = String.valueOf(ultimo.get("expedienteId"));
                }
            }
        }

        Vars.setStr(execution, "radicado", radicado);
        Vars.setStr(execution, "expedienteId", expedienteId);
        Vars.setStr(execution, "instancia", instancia);
        Vars.setStr(execution, "fechaRadicacion", Fechas.iso(fecha));
        Vars.setStr(execution, "radicacionLegible", calendario.legible(Fechas.iso(fecha)));
        Vars.setInt(execution, "mesesDesdeFactura", meses);
        Vars.setBool(execution, "esReapertura", esReapertura);
        Vars.setStr(execution, "radicadoAnterior", radicadoAnterior);
        if (!Vars.tiene(execution, "canalRadicacion")) {
            Vars.setStr(execution, "canalRadicacion", "VIRTUAL");
        }
        // valores por defecto de variables opcionales (formularios con campos ocultos)
        for (String k : new String[]{"apelacionSubsidiaria", "autorizaNotificacionElectronica", "requiereAccesibilidad", "procedenRecursos"}) {
            if (!Vars.tiene(execution, k)) {
                Vars.setBool(execution, k, false);
            }
        }
        for (String k : new String[]{"documentosAportados", "numeroFactura", "radicadoRecurrido", "nombrePeticionario",
                "direccionNotificacion", "correoNotificacion"}) {
            if (!Vars.tiene(execution, k)) {
                Vars.setStr(execution, k, "");
            }
        }
        // cada instrucción (primera instancia o recurso) empieza sin decisión previa
        Vars.setBool(execution, "ordenaAjusteC4", false);
        Vars.setBool(execution, "solicitaRevisionC6", false);
        Vars.setDbl(execution, "valorAjuste", 0);
        Vars.setBool(execution, "silencioPositivo", false);
        Vars.setBool(execution, "evidenciaOportuna", true);
        Vars.setStr(execution, "evidenciaRecibida", "");
        Vars.setStr(execution, "resultadoRevision", "");
        Vars.setStr(execution, "observacionesRevision", "");

        expediente.registrar(execution,
                "PRIMERA".equals(instancia) ? "PQR_RADICADA" : "RECURSO_RADICADO",
                "Radicar la PQR y asignar radicado",
                Vars.str(execution, "orientadorRadicador", "sistema"),
                datos("estado", "RADICADA", "radicado", radicado, "tipoPQR", tipo,
                        "asunto", Vars.str(execution, "asunto"),
                        "canal", Vars.str(execution, "canalRadicacion", "VIRTUAL"),
                        "codigoSuministro", Vars.str(execution, "codigoSuministro"),
                        "esReapertura", esReapertura, "radicadoAnterior", radicadoAnterior,
                        "mesesDesdeFactura", meses,
                        "radicadoRecurrido", Vars.str(execution, "radicadoRecurrido", ""),
                        "apelacionSubsidiaria", Vars.bool(execution, "apelacionSubsidiaria", false)));
        Vars.log("radicar-pqr", execution, radicado + " (" + instancia + ", " + tipo + "/" + Vars.str(execution, "asunto")
                + ") " + (esReapertura ? "· REAPERTURA de " + radicadoAnterior : ""));
    }
}
