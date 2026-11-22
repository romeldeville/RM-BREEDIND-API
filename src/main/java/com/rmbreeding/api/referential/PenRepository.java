package com.rmbreeding.api.referential;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PenRepository extends JpaRepository<PenEntity, UUID> {

    List<PenEntity> findByTenantIdAndBuildingIdAndArchivedAtIsNullOrderByName(UUID tenantId, UUID buildingId);

    Optional<PenEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIdAndTenantIdAndFarmId(UUID id, UUID tenantId, UUID farmId);
}
