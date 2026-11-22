package com.rmbreeding.api.reproduction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BirthRepository extends JpaRepository<BirthEntity, UUID> {

    List<BirthEntity> findByTenantIdAndCycleIdOrderByOccurredAtDesc(UUID tenantId, UUID cycleId);

    Optional<BirthEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantIdAndOccurredAtBetween(UUID tenantId, Instant from, Instant to);
}
