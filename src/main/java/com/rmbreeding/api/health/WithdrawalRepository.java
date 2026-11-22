package com.rmbreeding.api.health;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WithdrawalRepository extends JpaRepository<WithdrawalEntity, UUID> {

    List<WithdrawalEntity> findByTenantIdAndTreatmentId(UUID tenantId, UUID treatmentId);

    List<WithdrawalEntity> findByTenantIdAndAnimalIdOrderByEndDateDesc(UUID tenantId, UUID animalId);

    List<WithdrawalEntity> findByTenantIdAndStatusOrderByEndDateAsc(UUID tenantId, String status);

    List<WithdrawalEntity> findByTenantIdAndAnimalIdAndStatusAndEndDateGreaterThanEqual(
            UUID tenantId, UUID animalId, String status, LocalDate date);

    List<WithdrawalEntity> findByTenantIdAndLotIdAndStatusAndEndDateGreaterThanEqual(
            UUID tenantId, UUID lotId, String status, LocalDate date);
}
