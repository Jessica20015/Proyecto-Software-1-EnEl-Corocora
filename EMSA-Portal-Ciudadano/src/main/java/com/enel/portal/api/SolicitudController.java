package com.enel.portal.api;

import com.enel.shared.BusinessRequest;
import com.enel.shared.BusinessRequestRepository;
import com.enel.shared.BusinessRequestService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudController {

    private static final Set<String> C2_FIELDS = Set.of("nombre", "domicilio", "falla");
    private static final Set<String> C7_FIELDS = Set.of(
            "canalRadicacion", "tipoPQR", "asunto", "actoRecurrido", "apelacionSubsidiaria",
            "codigoSuministro", "calidadPeticionario", "nombrePeticionario", "descripcionPQR",
            "pretension", "numeroFactura", "fechaExpedicionFactura", "documentosAportados",
            "autorizaNotificacionElectronica", "correoNotificacion", "direccionNotificacion",
            "requiereAccesibilidad", "ajusteAccesibilidad", "autorizaTratamientoDatos");
    private static final String PQR_MESSAGE = "MsgPQRPortal";

    private final BusinessRequestRepository repository;
    private final BusinessRequestService solicitudService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String camundaUrl;

    public SolicitudController(
            BusinessRequestRepository repository,
            BusinessRequestService solicitudService,
            ObjectMapper objectMapper,
            RestTemplate restTemplate,
            @Value("${enel.camunda.base-url}") String camundaUrl) {
        this.repository = repository;
        this.solicitudService = solicitudService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.camundaUrl = camundaUrl.replaceAll("/+$", "");
    }

    @GetMapping("/plantillas/{proceso}")
    public Map<String, Object> plantilla(@PathVariable String proceso) {
        return switch (proceso.toUpperCase()) {
            case "C2" -> Map.of("nombre", "", "domicilio", "", "falla", "");
            case "C7" -> plantillaPqr();
            default -> throw new IllegalArgumentException("Proceso no soportado: " + proceso);
        };
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SolicitudResponse crear(@RequestBody CreateRequest request) {
        String proceso = normalizarProceso(request.proceso());
        Map<String, Object> datos = validarCampos(proceso, request.datos());
        String codigo = "SOL-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return respuesta(solicitudService.crear(codigo, proceso, datos));
    }

    @GetMapping("/{codigo}")
    public SolicitudResponse obtener(@PathVariable String codigo) {
        return respuesta(solicitudService.buscar(codigo));
    }

    @GetMapping
    public List<SolicitudResponse> historial() {
        return repository.findAll().stream().map(this::respuesta).toList();
    }

    @PutMapping("/{codigo}")
    public SolicitudResponse editar(@PathVariable String codigo, @RequestBody EditRequest request) {
        BusinessRequest actual = solicitudService.buscar(codigo);
        if (!"BORRADOR".equals(actual.getEstado())) {
            throw new IllegalStateException("Solo se pueden editar solicitudes en estado BORRADOR.");
        }
        return respuesta(solicitudService.actualizar(codigo, validarCampos(actual.getProceso(), request.datos())));
    }

    @PostMapping("/{codigo}/radicar")
    public SolicitudResponse radicar(@PathVariable String codigo) {
        BusinessRequest solicitud = solicitudService.buscar(codigo);
        if (!"BORRADOR".equals(solicitud.getEstado())) {
            throw new IllegalStateException("Solo se pueden radicar solicitudes en estado BORRADOR.");
        }
        Map<String, Object> datos = leerDatos(solicitud.getDatosJson());
        validarRequeridos(solicitud.getProceso(), datos);
        Map<String, Object> variables = variablesCamunda(solicitud.getProceso(), codigo, datos);
        String instancia = codigo;
        if ("C2".equals(solicitud.getProceso())) {
            Map<String, Object> start = new LinkedHashMap<>();
            start.put("businessKey", codigo);
            start.put("variables", variables);
            Map<?, ?> response = restTemplate.postForObject(
                    camundaUrl + "/process-definition/key/restablecerServicioTrasFalla/start",
                    start, Map.class);
            if (response != null && response.get("id") != null) {
                instancia = response.get("id").toString();
            }
        } else {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("messageName", PQR_MESSAGE);
            message.put("businessKey", codigo);
            message.put("processVariables", variables);
            restTemplate.postForEntity(camundaUrl + "/message", message, Void.class);
        }
        return respuesta(solicitudService.radicar(codigo, instancia));
    }

    @ExceptionHandler(BusinessRequestService.SolicitudNoEncontradaException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> noEncontrada(RuntimeException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> solicitudInvalida(RuntimeException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String, String>> errorCamunda(RestClientResponseException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", "Camunda rechazó la radicación: " + exception.getResponseBodyAsString()));
    }

    private SolicitudResponse respuesta(BusinessRequest request) {
        return new SolicitudResponse(request.getCodigoSolicitud(), request.getProceso(), request.getEstado(),
                leerDatos(request.getDatosJson()), request.getInstanciaProceso(),
                request.getCreadoEn(), request.getActualizadoEn());
    }

    private Map<String, Object> validarCampos(String proceso, Map<String, Object> datos) {
        if (datos == null) {
            throw new IllegalArgumentException("Los datos de la solicitud son obligatorios.");
        }
        Set<String> permitidos = "C2".equals(proceso) ? C2_FIELDS : C7_FIELDS;
        for (Map.Entry<String, Object> entrada : datos.entrySet()) {
            String campo = entrada.getKey();
            if (!permitidos.contains(campo)) {
                throw new IllegalArgumentException("El campo «" + campo + "» no pertenece al formulario " + proceso + ".");
            }
            if (entrada.getValue() == null) {
                throw new IllegalArgumentException("El campo «" + campo + "» no puede ser null.");
            }
        }
        return new LinkedHashMap<>(datos);
    }

    private void validarRequeridos(String proceso, Map<String, Object> datos) {
        List<String> campos = "C2".equals(proceso)
                ? List.of("nombre", "domicilio", "falla")
                : List.of("canalRadicacion", "tipoPQR", "asunto", "codigoSuministro", "calidadPeticionario",
                        "nombrePeticionario", "descripcionPQR", "pretension", "direccionNotificacion",
                        "autorizaTratamientoDatos");
        for (String campo : campos) {
            Object valor = datos.get(campo);
            if (valor == null || valor.toString().isBlank()) {
                throw new IllegalArgumentException("El campo obligatorio «" + campo + "» está vacío.");
            }
        }
        if ("C7".equals(proceso)) {
            if (!datos.get("codigoSuministro").toString().matches("SUM-[0-9]{6}")) {
                throw new IllegalArgumentException("codigoSuministro debe tener formato SUM-000000.");
            }
            if (datos.get("descripcionPQR").toString().trim().length() < 20) {
                throw new IllegalArgumentException("descripcionPQR debe tener al menos 20 caracteres.");
            }
            if (datos.get("pretension").toString().trim().length() < 10) {
                throw new IllegalArgumentException("pretension debe tener al menos 10 caracteres.");
            }
        }
    }

    private Map<String, Object> variablesCamunda(String proceso, String codigo, Map<String, Object> datos) {
        Map<String, Object> variables = new LinkedHashMap<>();
        datos.forEach((key, value) -> variables.put(key, Map.of("value", value, "type", tipoCamunda(value))));
        variables.put("codigoSolicitud", Map.of("value", codigo, "type", "String"));
        if ("C7".equals(proceso)) {
            variables.put("canalRadicacion", Map.of("value", "VIRTUAL", "type", "String"));
        }
        return variables;
    }

    private String tipoCamunda(Object value) {
        if (value instanceof Boolean) return "Boolean";
        if (value instanceof Integer || value instanceof Long) return "Long";
        if (value instanceof Number) return "Double";
        return "String";
    }

    private Map<String, Object> leerDatos(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Los datos persistidos de la solicitud no son JSON válido.", exception);
        }
    }

    private String normalizarProceso(String proceso) {
        if (proceso == null) throw new IllegalArgumentException("El proceso es obligatorio.");
        String normalizado = proceso.trim().toUpperCase();
        if (!Set.of("C2", "C7").contains(normalizado)) {
            throw new IllegalArgumentException("Solo están habilitados los procesos C2 y C7.");
        }
        return normalizado;
    }

    private Map<String, Object> plantillaPqr() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("canalRadicacion", "VIRTUAL");
        data.put("tipoPQR", "RECLAMO");
        data.put("asunto", "FACTURACION");
        data.put("codigoSuministro", "SUM-100208");
        data.put("calidadPeticionario", "SUSCRIPTOR");
        data.put("nombrePeticionario", "");
        data.put("descripcionPQR", "");
        data.put("pretension", "");
        data.put("numeroFactura", "FAC-2026-10-100208");
        data.put("fechaExpedicionFactura", "2026-11-05");
        data.put("documentosAportados", "");
        data.put("autorizaNotificacionElectronica", false);
        data.put("correoNotificacion", "");
        data.put("direccionNotificacion", "");
        data.put("requiereAccesibilidad", false);
        data.put("ajusteAccesibilidad", "");
        data.put("autorizaTratamientoDatos", true);
        return data;
    }

    public record CreateRequest(String proceso, Map<String, Object> datos) {}
    public record EditRequest(Map<String, Object> datos) {}
    public record SolicitudResponse(String codigoSolicitud, String proceso, String estado, Map<String, Object> datos,
                                    String instanciaProceso, java.time.Instant creadoEn, java.time.Instant actualizadoEn) {}
}
