package com.enel.central.api;

import com.enel.central.integration.CamundaTaskClient;
import com.enel.shared.BusinessRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

@RestController
public class TaskController {

    private static final Set<String> GROUPS = Set.of(
            "operador-control", "cuadrilla", "coordinador-zona", "analistas-facturacion",
            "calidad-facturacion", "liquidadores-tarifarios", "orientadores-pqr",
            "analistas-pqr", "juridica-pqr");

    private final CamundaTaskClient camunda;
    private final BusinessRequestService solicitudService;

    public TaskController(CamundaTaskClient camunda, BusinessRequestService solicitudService) {
        this.camunda = camunda;
        this.solicitudService = solicitudService;
    }

    @GetMapping("/task")
    public Object tareas(@RequestParam String candidateGroup) {
        validarGrupo(candidateGroup);
        return camunda.tareas(candidateGroup);
    }

    @GetMapping("/task/{id}/deployed-form")
    public Object formulario(@PathVariable String id) {
        return camunda.formulario(id);
    }

    @GetMapping("/task/{id}/variables")
    public Object variables(@PathVariable String id) {
        return camunda.variables(id);
    }

    @PostMapping("/task/{id}/claim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reclamar(@PathVariable String id, @RequestBody ClaimRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username es obligatorio.");
        }
        camunda.reclamar(id, request.username());
        sincronizarEstado(id, "EN_GESTION");
    }

    @PostMapping("/task/{id}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completar(@PathVariable String id, @RequestBody CompleteRequest request) {
        Map<String, Object> tarea = camunda.tarea(id);
        String processInstanceId = String.valueOf(tarea.get("processInstanceId"));
        String codigoSolicitud = codigoSolicitud(processInstanceId);
        camunda.completar(id, request.variables() == null ? Map.of() : request.variables());
        String estado = procesoActivo(processInstanceId) ? "EN_GESTION" : "FINALIZADA";
        if (codigoSolicitud != null && !codigoSolicitud.isBlank()) {
            try {
                solicitudService.cambiarEstado(codigoSolicitud, estado);
            } catch (BusinessRequestService.SolicitudNoEncontradaException ignored) {
                // Las instancias iniciadas fuera del Portal Ciudadano no tienen fila de dominio.
            }
        }
    }

    @ExceptionHandler(RestClientResponseException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> errorCamunda(RestClientResponseException e) {
        return org.springframework.http.ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", "Camunda respondió HTTP " + e.getStatusCode().value()
                        + ": " + e.getResponseBodyAsString()));
    }

    private String codigoSolicitud(String processInstanceId) {
        try {
            Map<String, Object> instancia = camunda.instancia(processInstanceId);
            return String.valueOf(instancia.getOrDefault("businessKey", ""));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) return null;
            throw exception;
        }
    }

    private boolean procesoActivo(String processInstanceId) {
        try {
            camunda.instancia(processInstanceId);
            return true;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) return false;
            throw exception;
        }
    }

    private void sincronizarEstado(String taskId, String estado) {
        Map<String, Object> tarea = camunda.tarea(taskId);
        String processInstanceId = String.valueOf(tarea.get("processInstanceId"));
        String codigo = codigoSolicitud(processInstanceId);
        if (codigo != null && !codigo.isBlank()) {
            try {
                solicitudService.cambiarEstado(codigo, estado);
            } catch (BusinessRequestService.SolicitudNoEncontradaException ignored) {
                // Las instancias iniciadas fuera del Portal Ciudadano no tienen fila de dominio.
            }
        }
    }

    private void validarGrupo(String grupo) {
        if (!GROUPS.contains(grupo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grupo de negocio no reconocido.");
        }
    }

    public record ClaimRequest(String username) {}
    public record CompleteRequest(Map<String, Object> variables) {}
}
