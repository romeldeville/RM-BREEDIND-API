package com.rmbreeding.api.production;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EggCollectionRepository extends JpaRepository<EggCollectionEntity, UUID> {

    List<EggCollectionEntity> findByTenantIdOrderByCollectedAtDesc(UUID tenantId);

    List<EggCollectionEntity> findByTenantIdAndLotIdOrderByCollectedAtDesc(UUID tenantId, UUID lotId);

    List<EggCollectionEntity> findByTenantIdAndLotIdAndCollectedAtBetween(
            UUID tenantId, UUID lotId, Instant from, Instant to);
}
