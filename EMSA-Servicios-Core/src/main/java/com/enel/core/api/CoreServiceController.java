package com.enel.core.api;

import com.enel.core.domain.TechnicalOrder;
import com.enel.core.domain.TechnicalOrderRepository;
import com.enel.shared.BusinessRequest;
import com.enel.shared.BusinessRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/core")
public class CoreServiceController {

    private final TechnicalOrderRepository orders;
    private final BusinessRequestService requests;

    public CoreServiceController(TechnicalOrderRepository orders, BusinessRequestService requests) {
        this.orders = orders;
        this.requests = requests;
    }

    @PostMapping("/liquidaciones")
    public LiquidationResponse liquidar(@RequestBody LiquidationRequest request) {
        if (request.cuKwh() < 0 || request.consumoFacturableKwh() < 0 || request.consumoSubsistenciaKwh() < 0) {
            throw new IllegalArgumentException("Los valores tarifarios y de consumo no pueden ser negativos.");
        }
        long valorEnergia = Math.round(request.cuKwh() * request.consumoFacturableKwh());
        long ajuste = 0;
        if (request.factorSubsidioContribucion() < 0) {
            ajuste = Math.round(request.factorSubsidioContribucion() * request.cuKwh()
                    * Math.min(request.consumoFacturableKwh(), request.consumoSubsistenciaKwh()));
        } else if (request.factorSubsidioContribucion() > 0) {
            ajuste = Math.round(request.factorSubsidioContribucion() * request.cuKwh()
                    * request.consumoFacturableKwh());
        }
        return new LiquidationResponse(valorEnergia, ajuste, valorEnergia + ajuste, Instant.now().toString());
    }

    @PostMapping("/ordenes-tecnicas")
    @ResponseStatus(HttpStatus.CREATED)
    public TechnicalOrder registrarOrden(@RequestBody TechnicalOrderRequest request) {
        if (request.codigoSolicitud() == null || request.codigoSolicitud().isBlank()) {
            throw new IllegalArgumentException("codigoSolicitud es obligatorio.");
        }
        return orders.save(new TechnicalOrder(request.codigoSolicitud(), request.codigoSuministro(), request.nivelDano()));
    }

    @GetMapping("/ordenes-tecnicas")
    public List<TechnicalOrder> ordenes(@RequestParam String codigoSolicitud) {
        return orders.findByCodigoSolicitudOrderByCreadoEnDesc(codigoSolicitud);
    }

    @GetMapping("/suministros/{codigo}/validacion")
    public Map<String, Object> validarSuministro(@PathVariable String codigo) {
        boolean formatoValido = codigo.matches("SUM-[0-9]{6}");
        return Map.of("codigoSuministro", codigo, "formatoValido", formatoValido);
    }

    @PutMapping("/solicitudes/{codigo}/estado")
    public BusinessRequest cambiarEstado(@PathVariable String codigo, @RequestBody StateRequest request) {
        if (!List.of("RADICADA", "EN_GESTION", "FINALIZADA", "CANCELADA").contains(request.estado())) {
            throw new IllegalArgumentException("Estado de solicitud no permitido.");
        }
        return requests.cambiarEstado(codigo, request.estado());
    }

    public record LiquidationRequest(double cuKwh, double consumoFacturableKwh,
                                    double factorSubsidioContribucion, double consumoSubsistenciaKwh) {}
    public record LiquidationResponse(long valorEnergia, long valorAjusteTarifario, long valorTotal,
                                      String fechaLiquidacion) {}
    public record TechnicalOrderRequest(String codigoSolicitud, String codigoSuministro, String nivelDano) {}
    public record StateRequest(String estado) {}
}
