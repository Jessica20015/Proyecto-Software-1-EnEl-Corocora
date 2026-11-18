package com.soft1.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class CoreServicesClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CoreServicesClient(RestTemplate restTemplate,
                              @Value("${emsa.servicios-core.base-url:http://localhost:8084}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public Liquidation liquidar(double cuKwh, double consumoFacturableKwh,
                                double factorSubsidioContribucion, double consumoSubsistenciaKwh) {
        return restTemplate.postForObject(baseUrl + "/api/core/liquidaciones",
                new LiquidationRequest(cuKwh, consumoFacturableKwh,
                        factorSubsidioContribucion, consumoSubsistenciaKwh), Liquidation.class);
    }

    public TechnicalOrder registrarOrden(String codigoSolicitud, String codigoSuministro,
                                         String nivelDano) {
        return restTemplate.postForObject(baseUrl + "/api/core/ordenes-tecnicas",
                new TechnicalOrderRequest(codigoSolicitud, codigoSuministro, nivelDano), TechnicalOrder.class);
    }

    public record LiquidationRequest(double cuKwh, double consumoFacturableKwh,
                                     double factorSubsidioContribucion, double consumoSubsistenciaKwh) {}
    public record Liquidation(long valorEnergia, long valorAjusteTarifario, long valorTotal,
                              String fechaLiquidacion) {}
    public record TechnicalOrderRequest(String codigoSolicitud, String codigoSuministro, String nivelDano) {}
    public record TechnicalOrder(String id, String codigoSolicitud, String codigoSuministro,
                                 String nivelDano, String estado, String creadoEn) {}
}
