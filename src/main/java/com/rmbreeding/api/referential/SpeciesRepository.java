package com.rmbreeding.api.referential;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpeciesRepository extends JpaRepository<SpeciesEntity, UUID> {

    List<SpeciesEntity> findByTenantIdAndArchivedAtIsNullOrderByName(UUID tenantId);

    List<SpeciesEntity> findByTenantIdOrderByName(UUID tenantId);

    Optional<SpeciesEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
