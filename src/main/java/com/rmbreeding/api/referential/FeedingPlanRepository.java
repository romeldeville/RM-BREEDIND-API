package com.rmbreeding.api.referential;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeedingPlanRepository extends JpaRepository<FeedingPlanEntity, UUID> {

    List<FeedingPlanEntity> findByTenantIdAndSpeciesIdOrderByName(UUID tenantId, UUID speciesId);

    List<FeedingPlanEntity> findByTenantIdOrderByName(UUID tenantId);

    Optional<FeedingPlanEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
