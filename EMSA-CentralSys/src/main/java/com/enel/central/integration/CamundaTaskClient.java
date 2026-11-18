package com.enel.central.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Component
public class CamundaTaskClient {

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
        return restTemplate.exchange(uri, HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}).getBody();
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
