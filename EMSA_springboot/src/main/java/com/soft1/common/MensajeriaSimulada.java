package com.soft1.common;

import jakarta.annotation.PreDestroy;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.MessageCorrelationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Simula a los participantes externos (C3, C5, G3, operador de entrega, el usuario que recurre...).
 * <p>
 * En los workers de Node.js esto se hacía con setTimeout + POST /engine-rest/message. Aquí se hace igual,
 * pero llamando directamente al RuntimeService y fuera de la transacción del delegate: el mensaje se
 * correlaciona unos segundos después, con reintentos (la instancia puede no haber llegado aún al evento).
 */
@Component
public class MensajeriaSimulada {

    private static final Logger LOG = LoggerFactory.getLogger("emsa");
    private static final int INTENTOS = 10;
    private static final long ESPERA_ENTRE_INTENTOS_MS = 1500;

    private final RuntimeService runtimeService;
    private final ScheduledExecutorService pool = Executors.newScheduledThreadPool(4, r -> {
        Thread t = new Thread(r, "emsa-mensajeria");
        t.setDaemon(true);
        return t;
    });

    public MensajeriaSimulada(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    /** Correlaciona el mensaje con una instancia concreta, tras el retraso indicado. */
    public void porInstancia(long retrasoMs, String mensaje, String processInstanceId, Map<String, Object> variables) {
        programar(retrasoMs, mensaje, b -> b.processInstanceId(processInstanceId), variables,
                "instancia " + processInstanceId);
    }

    /** Correlaciona el mensaje con la instancia que tenga esa clave de negocio, tras el retraso indicado. */
    public void porBusinessKey(long retrasoMs, String mensaje, String businessKey, Map<String, Object> variables) {
        programar(retrasoMs, mensaje, b -> b.processInstanceBusinessKey(businessKey), variables,
                "businessKey " + businessKey);
    }

    private void programar(long retrasoMs, String mensaje, Consumer<MessageCorrelationBuilder> destino,
                           Map<String, Object> variables, String descripcion) {
        pool.schedule(() -> correlacionar(mensaje, destino, variables, descripcion), retrasoMs, TimeUnit.MILLISECONDS);
    }

    private void correlacionar(String mensaje, Consumer<MessageCorrelationBuilder> destino,
                               Map<String, Object> variables, String descripcion) {
        for (int intento = 1; intento <= INTENTOS; intento++) {
            try {
                MessageCorrelationBuilder b = runtimeService.createMessageCorrelation(mensaje);
                destino.accept(b);
                b.setVariables(variables).correlate();
                LOG.info("    ↳ mensaje {} correlacionado ({})", mensaje, descripcion);
                return;
            } catch (Exception e) {
                if (intento == INTENTOS) {
                    LOG.warn("    ↳ ¡no se pudo correlacionar {}! ({}): {}", mensaje, descripcion, e.getMessage());
                    return;
                }
                try {
                    Thread.sleep(ESPERA_ENTRE_INTENTOS_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @PreDestroy
    void cerrar() {
        pool.shutdownNow();
    }
}
