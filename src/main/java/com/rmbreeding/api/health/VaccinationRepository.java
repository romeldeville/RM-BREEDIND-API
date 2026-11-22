package com.rmbreeding.api.health;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VaccinationRepository extends JpaRepository<VaccinationEntity, UUID> {

    List<VaccinationEntity> findByTenantIdOrderByPerformedAtDesc(UUID tenantId);

    List<VaccinationEntity> findByTenantIdAndAnimalIdOrderByPerformedAtDesc(UUID tenantId, UUID animalId);

    List<VaccinationEntity> findByTenantIdAndLotIdOrderByPerformedAtDesc(UUID tenantId, UUID lotId);
}
