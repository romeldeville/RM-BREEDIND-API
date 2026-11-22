package com.rmbreeding.api.health;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreatmentRepository extends JpaRepository<TreatmentEntity, UUID> {

    Optional<TreatmentEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<TreatmentEntity> findByTenantIdOrderByStartDateDesc(UUID tenantId);

    List<TreatmentEntity> findByTenantIdAndAnimalIdOrderByStartDateDesc(UUID tenantId, UUID animalId);

    List<TreatmentEntity> findByTenantIdAndLotIdOrderByStartDateDesc(UUID tenantId, UUID lotId);

    List<TreatmentEntity> findByTenantIdAndStatusOrderByStartDateDesc(UUID tenantId, String status);
}
