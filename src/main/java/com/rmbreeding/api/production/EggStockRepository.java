package com.rmbreeding.api.production;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EggStockRepository extends JpaRepository<EggStockEntity, UUID> {

    Optional<EggStockEntity> findByTenantIdAndFarmIdAndLotId(UUID tenantId, UUID farmId, UUID lotId);

    Optional<EggStockEntity> findByTenantIdAndFarmIdAndLotIdIsNull(UUID tenantId, UUID farmId);

    List<EggStockEntity> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);
}
