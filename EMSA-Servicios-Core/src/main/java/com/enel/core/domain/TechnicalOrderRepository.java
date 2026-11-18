package com.enel.core.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TechnicalOrderRepository extends JpaRepository<TechnicalOrder, String> {
    List<TechnicalOrder> findByCodigoSolicitudOrderByCreadoEnDesc(String codigoSolicitud);
}
