package com.rmbreeding.api.health;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TreatmentAdministrationRepository extends JpaRepository<TreatmentAdministrationEntity, UUID> {

    List<TreatmentAdministrationEntity> findByTenantIdAndTreatmentIdOrderByAdministeredAtDesc(
            UUID tenantId, UUID treatmentId);
}
