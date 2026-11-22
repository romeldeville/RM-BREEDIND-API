package com.rmbreeding.api.reproduction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatingRepository extends JpaRepository<MatingEntity, UUID> {

    List<MatingEntity> findByTenantIdAndCycleIdOrderByMatedAtDesc(UUID tenantId, UUID cycleId);

    Optional<MatingEntity> findFirstByTenantIdAndCycleIdOrderByMatedAtDesc(UUID tenantId, UUID cycleId);

    boolean existsByTenantIdAndCycleId(UUID tenantId, UUID cycleId);

    long countByTenantIdAndMatedAtBetween(UUID tenantId, Instant from, Instant to);
}
