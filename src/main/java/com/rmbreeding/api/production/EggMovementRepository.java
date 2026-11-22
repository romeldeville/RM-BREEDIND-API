package com.rmbreeding.api.production;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EggMovementRepository extends JpaRepository<EggMovementEntity, UUID> {

    List<EggMovementEntity> findByTenantIdOrderByOccurredAtDesc(UUID tenantId);

    List<EggMovementEntity> findByTenantIdAndLotIdOrderByOccurredAtDesc(UUID tenantId, UUID lotId);
}
