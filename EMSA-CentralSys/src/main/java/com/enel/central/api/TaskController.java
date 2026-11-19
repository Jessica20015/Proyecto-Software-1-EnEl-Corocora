package com.enel.central.api;

import com.enel.central.integration.CamundaTaskClient;
import com.enel.central.seguridad.Funcionario;
import com.enel.shared.BusinessRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API de tareas para funcionarios. Requiere sesión (SesionInterceptor) y autoriza cada operación con los
 * grupos del funcionario: solo ve y reclama tareas de sus carriles, y solo completa las que reclamó.
 */
@RestController
public class TaskController {

    public static final Set<String> GROUPS = Set.of(
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
    public Object tareas(@RequestParam String candidateGroup,
                         @SessionAttribute(Funcionario.ATRIBUTO_SESION) Funcionario funcionario) {
        validarGrupo(candidateGroup);
        if (!funcionario.perteneceA(candidateGroup)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No pertenece al carril «" + candidateGroup + "».");
        }
        return camunda.tareas(candidateGroup);
    }

    /** Tareas que el funcionario ya reclamó: al reclamarlas dejan de aparecer en la lista del carril. */
    @GetMapping("/task/mias")
    public Object misTareas(@SessionAttribute(Funcionario.ATRIBUTO_SESION) Funcionario funcionario) {
        return camunda.tareasAsignadas(funcionario.usuario());
    }

    @GetMapping("/task/{id}/deployed-form")
    public Object formulario(@PathVariable String id,
                             @SessionAttribute(Funcionario.ATRIBUTO_SESION) Funcionario funcionario) {
        autorizarConsulta(camunda.tarea(id), id, funcionario);
        return camunda.formulario(id);
    }

    @GetMapping("/task/{id}/variables")
    public Object variables(@PathVariable String id,
                            @SessionAttribute(Funcionario.ATRIBUTO_SESION) Funcionario funcionario) {
        autorizarConsulta(camunda.tarea(id), id, funcionario);
        return camunda.variables(id);
    }

    /** Reclama la tarea para el funcionario de la sesión (el cuerpo ya no indica el usuario). */
    @PostMapping("/task/{id}/claim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reclamar(@PathVariable String id,
                         @SessionAttribute(Funcionario.ATRIBUTO_SESION) Funcionario funcionario) {
        Map<String, Object> tarea = camunda.tarea(id);
        Object asignada = tarea.get("assignee");
        if (funcionario.usuario().equals(asignada)) {
            return;   // ya es suya
        }
        if (asignada != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La tarea ya fue reclamada por «" + asignada + "».");
        }
        if (!esCandidato(id, funcionario)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La tarea no pertenece a ninguno de sus carriles.");
        }
        camunda.reclamar(id, funcionario.usuario());
        sincronizarEstado(tarea, "EN_GESTION");
    }

    @PostMapping("/task/{id}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completar(@PathVariable String id, @RequestBody CompleteRequest request,
                          @SessionAttribute(Funcionario.ATRIBUTO_SESION) Funcionario funcionario) {
        Map<String, Object> tarea = camunda.tarea(id);
        if (!funcionario.usuario().equals(tarea.get("assignee"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reclame la tarea antes de completarla.");
        }
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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Error" : e.getReason()));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String, String>> errorCamunda(RestClientResponseException e) {
        String cuerpo = e.getResponseBodyAsString();
        // Un task listener que rechaza los datos (validación del proceso) es un error del formulario, no del
        // motor: se responde 400 con solo el mensaje de la validación para mostrárselo al funcionario.
        Matcher rechazo = RECHAZO_LISTENER.matcher(cuerpo);
        if (rechazo.find()) {
            return ResponseEntity.badRequest().body(Map.of("message", rechazo.group(1).trim()));
        }
        // una tarea inexistente (o ya completada) es un 404 para quien consulta, no un fallo del motor
        HttpStatus estado = e.getStatusCode().value() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(estado)
                .body(Map.of("message", "Camunda respondió HTTP " + e.getStatusCode().value() + ": " + cuerpo));
    }

    /** Mensaje de Camunda cuando un TaskListener lanza una excepción: se extrae el texto de la excepción. */
    private static final Pattern RECHAZO_LISTENER =
            Pattern.compile("Exception while invoking TaskListener: (.+?)'\\s*\"");

    /** Puede ver la tarea si la reclamó, o si está libre y es de uno de sus carriles. */
    private void autorizarConsulta(Map<String, Object> tarea, String id, Funcionario funcionario) {
        Object asignada = tarea.get("assignee");
        boolean permitido = asignada == null ? esCandidato(id, funcionario) : funcionario.usuario().equals(asignada);
        if (!permitido) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene acceso a esta tarea.");
        }
    }

    private boolean esCandidato(String taskId, Funcionario funcionario) {
        List<String> candidatos = camunda.gruposCandidatos(taskId);
        return candidatos.stream().anyMatch(funcionario::perteneceA);
    }

    private String codigoSolicitud(String processInstanceId) {
        try {
            Object clave = camunda.instancia(processInstanceId).get("businessKey");
            return clave == null ? null : clave.toString();
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

    private void sincronizarEstado(Map<String, Object> tarea, String estado) {
        String codigo = codigoSolicitud(String.valueOf(tarea.get("processInstanceId")));
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

    public record CompleteRequest(Map<String, Object> variables) {}
}
