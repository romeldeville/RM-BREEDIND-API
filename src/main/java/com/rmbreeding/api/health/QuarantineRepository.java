package com.rmbreeding.api.health;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuarantineRepository extends JpaRepository<QuarantineEntity, UUID> {

    Optional<QuarantineEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<QuarantineEntity> findByTenantIdOrderByStartDateDesc(UUID tenantId);

    List<QuarantineEntity> findByTenantIdAndStatusOrderByStartDateDesc(UUID tenantId, String status);

    List<QuarantineEntity> findByTenantIdAndAnimalIdOrderByStartDateDesc(UUID tenantId, UUID animalId);
}
