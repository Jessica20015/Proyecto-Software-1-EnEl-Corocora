package com.enel.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enel_ordenes_tecnicas")
public class TechnicalOrder {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "codigo_solicitud", nullable = false, length = 80)
    private String codigoSolicitud;

    @Column(name = "codigo_suministro", length = 40)
    private String codigoSuministro;

    @Column(name = "nivel_dano", length = 40)
    private String nivelDano;

    @Column(length = 30, nullable = false)
    private String estado;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected TechnicalOrder() {
    }

    public TechnicalOrder(String codigoSolicitud, String codigoSuministro, String nivelDano) {
        this.id = UUID.randomUUID().toString();
        this.codigoSolicitud = codigoSolicitud;
        this.codigoSuministro = codigoSuministro;
        this.nivelDano = nivelDano;
        this.estado = "REGISTRADA";
        this.creadoEn = Instant.now();
    }

    public String getId() { return id; }
    public String getCodigoSolicitud() { return codigoSolicitud; }
    public String getCodigoSuministro() { return codigoSuministro; }
    public String getNivelDano() { return nivelDano; }
    public String getEstado() { return estado; }
    public Instant getCreadoEn() { return creadoEn; }
}
