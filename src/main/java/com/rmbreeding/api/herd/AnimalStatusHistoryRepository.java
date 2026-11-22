package com.rmbreeding.api.herd;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnimalStatusHistoryRepository extends JpaRepository<AnimalStatusHistoryEntity, UUID> {

    List<AnimalStatusHistoryEntity> findByTenantIdAndAnimalIdOrderByChangedAtDesc(UUID tenantId, UUID animalId);
}
