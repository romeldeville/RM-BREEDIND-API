package com.rmbreeding.api.referential;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpeciesTemplateRepository extends JpaRepository<SpeciesTemplateEntity, UUID> {

    List<SpeciesTemplateEntity> findByTenantIdAndSpeciesIdOrderByName(UUID tenantId, UUID speciesId);

    Optional<SpeciesTemplateEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
