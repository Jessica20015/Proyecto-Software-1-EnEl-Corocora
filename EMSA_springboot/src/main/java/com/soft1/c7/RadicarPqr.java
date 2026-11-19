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
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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
        validarEntrada(execution);
        // la instancia sale de tipoPQR (no de la variable "instancia", que en un recurso aún dice PRIMERA):
        // el recurso por mensaje llega con tipoPQR = RECURSO_REPOSICION (listener de Event_RecursoRecibido)
        String tipo = Vars.str(execution, "tipoPQR");
        String instancia = "RECURSO_REPOSICION".equals(tipo) ? "REPOSICION" : "PRIMERA";
        Instant fecha = Instant.now();

        // meses desde la expedición de la factura reclamada (art. 154, Ley 142: cinco meses).
        // El campo es opcional: vacío (el Tasklist envía "" si está visible y sin llenar), corto o inválido = 0 meses,
        // como el worker JS (if (fFactura)). Se calcula antes de tomar el radicado para no gastar un consecutivo.
        int meses = 0;
        String fFactura = Vars.str(execution, "fechaExpedicionFactura", "").trim();
        if (!fFactura.isEmpty()) {
            try {
                LocalDate f = LocalDate.parse(fFactura.substring(0, Math.min(10, fFactura.length())));
                ZonedDateTime hoy = fecha.atZone(ZoneOffset.UTC);
                meses = (hoy.getYear() - f.getYear()) * 12 + (hoy.getMonthValue() - f.getMonthValue())
                        - (hoy.getDayOfMonth() < f.getDayOfMonth() ? 1 : 0);
            } catch (DateTimeParseException ex) {
                Vars.log("radicar-pqr", execution, "fecha de expedición de la factura no válida «" + fFactura
                        + "»: se toman 0 meses");
            }
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

        // el consecutivo no se devuelve si la transacción falla: se toma después de los cálculos que leen la entrada
        String radicado = expediente.siguienteRadicado();
        String expedienteId = Vars.str(execution, "expedienteId", radicado);
        if (execution.getProcessBusinessKey() == null || execution.getProcessBusinessKey().isBlank()) {
            String codigoSolicitud = Vars.str(execution, "codigoSolicitud", "").trim();
            execution.setProcessBusinessKey(codigoSolicitud.isEmpty() ? expedienteId : codigoSolicitud);
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
        // recurso radicado en ventanilla (formulario de inicio): el número del acto recurrido es el que ve el analista
        // como "Radicado de la decisión recurrida". El recurso por mensaje ya lo trae (radicado de la primera instancia).
        if ("REPOSICION".equals(instancia) && Vars.str(execution, "radicadoRecurrido", "").isBlank()) {
            String acto = Vars.str(execution, "actoRecurrido", "").trim();
            if (!acto.isEmpty()) {
                Vars.setStr(execution, "radicadoRecurrido", acto);
            }
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
        // cada instrucción (primera instancia o recurso) empieza sin decisión previa.
        // Las variables que llena un Camunda Form se ELIMINAN en lugar de ponerse en false/0/"": el Tasklist 7.24
        // carga todas las variables en el formulario y una variable existente (aunque valga false, 0 o "")
        // reemplaza el defaultValue del campo. Es seguro: la compuerta «¿Ordena ajuste en C4?» usa
        // execution.getVariable('ordenaAjusteC4'), que tolera la variable ausente; ${resultadoRevision} solo se
        // evalúa después de RevisarProyectoRespuesta (radio obligatorio); EnviarAjusteC4 tolera valorAjuste ausente.
        if ("PRIMERA".equals(instancia)) {
            execution.removeVariable("ordenaAjusteC4");
            execution.removeVariable("valorAjuste");
        } else {
            // en el recurso no se propone por defecto un segundo ajuste en C4 (el analista lo marca si procede)
            Vars.setBool(execution, "ordenaAjusteC4", false);
            Vars.setDbl(execution, "valorAjuste", 0);
        }
        execution.removeVariable("resultadoRevision");
        execution.removeVariable("observacionesRevision");
        // solicitaRevisionC6 está oculto salvo en RECUPERACION_CONSUMOS, pero la compuerta ${solicitaRevisionC6} lo lee siempre
        Vars.setBool(execution, "solicitaRevisionC6", false);
        Vars.setBool(execution, "silencioPositivo", false);
        Vars.setBool(execution, "evidenciaOportuna", true);
        Vars.setStr(execution, "evidenciaRecibida", "");

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

    private static final Set<String> CANALES = Set.of("PRESENCIAL", "TELEFONICO", "VIRTUAL");
    private static final Set<String> ASUNTOS = Set.of("FACTURACION", "MEDICION", "SUSPENSION_RECONEXION",
            "RECUPERACION_CONSUMOS", "CALIDAD_CONTINUIDAD", "ATENCION_SERVICIO", "OTRO");
    private static final Set<String> CALIDADES = Set.of("SUSCRIPTOR", "USUARIO", "TERCERO_AUTORIZADO");
    private static final Pattern SUMINISTRO = Pattern.compile("SUM-[0-9]{6}");
    /** AAAA-MM-DD como en el formulario; se tolera una hora ISO al final (la fecha se toma de los 10 primeros caracteres). */
    private static final Pattern FECHA = Pattern.compile("[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])(T.*)?");

    /**
     * Validación de la entrada de la PQR (formulario RadicarPQR, mensaje del portal o recurso). Corre en la misma
     * transacción del inicio: si un dato no tiene el tipo o el formato esperado, la PQR no se radica, no se gasta
     * un consecutivo y quien la envió recibe la lista de errores. Antes, una fecha inválida se tomaba como 0 meses
     * y un booleano mal escrito como «no».
     */
    private static void validarEntrada(DelegateExecution execution) {
        ValidacionC7 v = new ValidacionC7(execution, "Radicación de la PQR")
                .opcion("canalRadicacion", CANALES, false)
                .opcion("tipoPQR", ValidarFormularioC7.TIPOS_PQR, true)
                .opcion("asunto", ASUNTOS, true)
                .patron("codigoSuministro", SUMINISTRO, "SUM-000000", true)
                .opcion("calidadPeticionario", CALIDADES, true)
                .texto("nombrePeticionario", 3)
                .texto("descripcionPQR", 20)
                .texto("pretension", 10)
                .texto("direccionNotificacion", 1)
                .patron("fechaExpedicionFactura", FECHA, "AAAA-MM-DD", false)
                .booleano("autorizaTratamientoDatos", true, true)
                .booleano("autorizaNotificacionElectronica", false, false)
                .booleano("requiereAccesibilidad", false, false)
                .booleano("apelacionSubsidiaria", false, false);
        // con notificación electrónica autorizada, el correo es obligatorio (sin él no hay a dónde notificar)
        if (Boolean.TRUE.equals(execution.getVariable("autorizaNotificacionElectronica"))) {
            v.correo("correoNotificacion");
        }
        v.verificar();
    }
}
