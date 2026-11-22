package com.rmbreeding.api.reproduction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AbortionRepository extends JpaRepository<AbortionEntity, UUID> {

    List<AbortionEntity> findByTenantIdAndCycleIdOrderByOccurredAtDesc(UUID tenantId, UUID cycleId);

    long countByTenantIdAndOccurredAtBetween(UUID tenantId, Instant from, Instant to);
}
