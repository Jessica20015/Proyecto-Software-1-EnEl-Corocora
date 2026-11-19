package com.enel.central.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class CamundaTaskClient {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CamundaTaskClient(RestTemplate restTemplate, @Value("${enel.camunda.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public List<Map<String, Object>> tareas(String grupo) {
        String uri = UriComponentsBuilder.fromUriString(baseUrl + "/task")
                .queryParam("candidateGroup", grupo)
                .queryParam("active", true)
                .build().encode().toUriString();
        return restTemplate.exchange(uri, HttpMethod.GET, null, LISTA).getBody();
    }

    /** Tareas activas asignadas (reclamadas) por el usuario. */
    public List<Map<String, Object>> tareasAsignadas(String usuario) {
        String uri = UriComponentsBuilder.fromUriString(baseUrl + "/task")
                .queryParam("assignee", usuario)
                .queryParam("active", true)
                .build().encode().toUriString();
        return restTemplate.exchange(uri, HttpMethod.GET, null, LISTA).getBody();
    }

    /** Grupos candidatos de la tarea (camunda:candidateGroups). */
    public List<String> gruposCandidatos(String taskId) {
        String uri = UriComponentsBuilder.fromUriString(baseUrl + "/task/{id}/identity-links")
                .queryParam("type", "candidate").buildAndExpand(taskId).encode().toUriString();
        List<Map<String, Object>> enlaces = restTemplate.exchange(uri, HttpMethod.GET, null, LISTA).getBody();
        return enlaces == null ? List.of() : enlaces.stream()
                .map(e -> e.get("groupId")).filter(Objects::nonNull).map(Object::toString).toList();
    }

    /** Verifica usuario y clave contra el servicio de identidad del motor. */
    public boolean verificar(String usuario, String clave) {
        Map<?, ?> r = restTemplate.postForObject(baseUrl + "/identity/verify",
                Map.of("username", usuario, "password", clave), Map.class);
        return r != null && Boolean.TRUE.equals(r.get("authenticated"));
    }

    /** Identificadores de los grupos a los que pertenece el usuario. */
    public List<String> grupos(String usuario) {
        String uri = UriComponentsBuilder.fromUriString(baseUrl + "/group")
                .queryParam("member", usuario).build().encode().toUriString();
        List<Map<String, Object>> grupos = restTemplate.exchange(uri, HttpMethod.GET, null, LISTA).getBody();
        return grupos == null ? List.of() : grupos.stream().map(g -> String.valueOf(g.get("id"))).toList();
    }

    public Map<String, Object> formulario(String taskId) {
        return restTemplate.getForObject(baseUrl + "/task/{id}/deployed-form", Map.class, taskId);
    }

    public Map<String, Object> variables(String taskId) {
        String uri = UriComponentsBuilder.fromUriString(baseUrl + "/task/{id}/form-variables")
                .queryParam("deserializeValues", false).buildAndExpand(taskId).encode().toUriString();
        return restTemplate.exchange(uri, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
    }

    public Map<String, Object> tarea(String taskId) {
        return restTemplate.getForObject(baseUrl + "/task/{id}", Map.class, taskId);
    }

    public void reclamar(String taskId, String username) {
        restTemplate.postForEntity(baseUrl + "/task/{id}/claim", Map.of("userId", username), Void.class, taskId);
    }

    public void completar(String taskId, Map<String, Object> variables) {
        restTemplate.postForEntity(baseUrl + "/task/{id}/complete",
                Map.of("variables", variables), Void.class, taskId);
    }

    public Map<String, Object> instancia(String processInstanceId) {
        return restTemplate.getForObject(baseUrl + "/process-instance/{id}", Map.class, processInstanceId);
    }
}
