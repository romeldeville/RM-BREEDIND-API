package com.rmbreeding.api.health;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MortalityRepository extends JpaRepository<MortalityEntity, UUID> {

    List<MortalityEntity> findByTenantIdOrderByOccurredAtDesc(UUID tenantId);

    List<MortalityEntity> findByTenantIdAndAnimalIdOrderByOccurredAtDesc(UUID tenantId, UUID animalId);
}
