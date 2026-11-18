package com.enel.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class BusinessRequestService {

    private final BusinessRequestRepository repository;
    private final ObjectMapper objectMapper;

    public BusinessRequestService(BusinessRequestRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BusinessRequest crear(String codigo, String proceso, Map<String, Object> datos) {
        return repository.save(new BusinessRequest(codigo, proceso, "BORRADOR", serializar(datos)));
    }

    @Transactional
    public BusinessRequest actualizar(String codigo, Map<String, Object> datos) {
        BusinessRequest solicitud = buscar(codigo);
        solicitud.actualizar(serializar(datos));
        return repository.save(solicitud);
    }

    @Transactional(readOnly = true)
    public BusinessRequest buscar(String codigo) {
        return repository.findById(codigo)
                .orElseThrow(() -> new SolicitudNoEncontradaException(codigo));
    }

    @Transactional
    public BusinessRequest radicar(String codigo, String instancia) {
        BusinessRequest solicitud = buscar(codigo);
        solicitud.radicar(instancia);
        return repository.save(solicitud);
    }

    @Transactional
    public BusinessRequest cambiarEstado(String codigo, String estado) {
        BusinessRequest solicitud = buscar(codigo);
        solicitud.cambiarEstado(estado);
        return repository.save(solicitud);
    }

    private String serializar(Map<String, Object> datos) {
        try {
            return objectMapper.writeValueAsString(datos);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("No fue posible serializar los datos de la solicitud.", e);
        }
    }

    public static class SolicitudNoEncontradaException extends RuntimeException {
        public SolicitudNoEncontradaException(String codigo) {
            super("No existe la solicitud " + codigo + ".");
        }
    }
}
