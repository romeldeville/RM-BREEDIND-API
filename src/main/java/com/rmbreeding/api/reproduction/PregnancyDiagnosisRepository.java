package com.rmbreeding.api.reproduction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PregnancyDiagnosisRepository extends JpaRepository<PregnancyDiagnosisEntity, UUID> {

    List<PregnancyDiagnosisEntity> findByTenantIdAndCycleIdOrderByDiagnosedAtDesc(
            UUID tenantId, UUID cycleId);

    long countByTenantIdAndResultAndDiagnosedAtBetween(
            UUID tenantId, String result, Instant from, Instant to);
}
