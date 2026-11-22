package com.rmbreeding.api.referential;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GrowthCurveRepository extends JpaRepository<GrowthCurveEntity, UUID> {

    List<GrowthCurveEntity> findByTenantIdAndSpeciesIdOrderByName(UUID tenantId, UUID speciesId);

    Optional<GrowthCurveEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
