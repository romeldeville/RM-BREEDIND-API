package com.rmbreeding.api.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<SaleEntity, UUID> {

    Optional<SaleEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SaleEntity> findByTenantIdOrderBySoldAtDesc(UUID tenantId);

    List<SaleEntity> findByTenantIdAndStatusOrderBySoldAtDesc(UUID tenantId, String status);

    List<SaleEntity> findByTenantIdAndSoldAtBetweenOrderBySoldAtDesc(UUID tenantId, Instant from, Instant to);

    long countByTenantId(UUID tenantId);
}
