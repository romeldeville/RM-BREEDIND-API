package com.rmbreeding.api.reproduction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HeatRepository extends JpaRepository<HeatEntity, UUID> {

    List<HeatEntity> findByTenantIdAndCycleIdOrderByObservedAtDesc(UUID tenantId, UUID cycleId);
}
