package com.rmbreeding.api.referential;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VaccinationPlanRepository extends JpaRepository<VaccinationPlanEntity, UUID> {

    List<VaccinationPlanEntity> findByTenantIdAndSpeciesIdOrderByName(UUID tenantId, UUID speciesId);

    List<VaccinationPlanEntity> findByTenantIdOrderByName(UUID tenantId);

    Optional<VaccinationPlanEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
