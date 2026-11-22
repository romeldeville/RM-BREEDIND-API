package com.rmbreeding.api.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SaleLineRepository extends JpaRepository<SaleLineEntity, UUID> {

    List<SaleLineEntity> findByTenantIdAndSaleId(UUID tenantId, UUID saleId);

    List<SaleLineEntity> findByTenantIdAndLotId(UUID tenantId, UUID lotId);
}
