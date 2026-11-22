package com.rmbreeding.api.referential;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BreedRepository extends JpaRepository<BreedEntity, UUID> {

    List<BreedEntity> findByTenantIdAndSpeciesIdAndArchivedAtIsNullOrderByName(UUID tenantId, UUID speciesId);

    Optional<BreedEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIdAndTenantIdAndSpeciesId(UUID id, UUID tenantId, UUID speciesId);

    boolean existsByTenantIdAndSpeciesIdAndName(UUID tenantId, UUID speciesId, String name);
}
