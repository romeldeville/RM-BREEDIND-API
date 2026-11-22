package com.rmbreeding.api.health;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HealthEventRepository extends JpaRepository<HealthEventEntity, UUID> {

    List<HealthEventEntity> findByTenantIdOrderByOccurredAtDesc(UUID tenantId);

    List<HealthEventEntity> findByTenantIdAndAnimalIdOrderByOccurredAtDesc(UUID tenantId, UUID animalId);
}
