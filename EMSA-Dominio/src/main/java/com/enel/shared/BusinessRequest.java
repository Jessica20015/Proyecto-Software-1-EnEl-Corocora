package com.enel.shared;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "enel_solicitudes")
public class BusinessRequest {

    @Id
    @Column(name = "codigo_solicitud", nullable = false, length = 80)
    private String codigoSolicitud;

    @Column(nullable = false, length = 40)
    private String proceso;

    @Column(nullable = false, length = 30)
    private String estado;

    @Column(name = "datos_json", nullable = false, columnDefinition = "text")
    private String datosJson;

    @Column(name = "instancia_proceso", length = 80)
    private String instanciaProceso;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Version
    private long version;

    protected BusinessRequest() {
    }

    public BusinessRequest(String codigoSolicitud, String proceso, String estado, String datosJson) {
        this.codigoSolicitud = codigoSolicitud;
        this.proceso = proceso;
        this.estado = estado;
        this.datosJson = datosJson;
        this.creadoEn = Instant.now();
        this.actualizadoEn = this.creadoEn;
    }

    public String getCodigoSolicitud() {
        return codigoSolicitud;
    }

    public String getProceso() {
        return proceso;
    }

    public String getEstado() {
        return estado;
    }

    public String getDatosJson() {
        return datosJson;
    }

    public String getInstanciaProceso() {
        return instanciaProceso;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public void actualizar(String datosJson) {
        if (!"BORRADOR".equals(estado)) {
            throw new IllegalStateException("Solo se pueden editar solicitudes en estado BORRADOR.");
        }
        this.datosJson = datosJson;
        this.actualizadoEn = Instant.now();
    }

    public void radicar(String instanciaProceso) {
        if (!"BORRADOR".equals(estado)) {
            throw new IllegalStateException("La solicitud ya fue radicada.");
        }
        this.estado = "RADICADA";
        this.instanciaProceso = instanciaProceso;
        this.actualizadoEn = Instant.now();
    }

    public void cambiarEstado(String estado) {
        this.estado = estado;
        this.actualizadoEn = Instant.now();
    }
}
