package com.rmbreeding.api.herd;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnimalRepository extends JpaRepository<AnimalEntity, UUID>,
        JpaSpecificationExecutor<AnimalEntity> {

    Optional<AnimalEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndFarmIdAndIdentifier(UUID tenantId, UUID farmId, String identifier);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndArchivedAtIsNull(UUID tenantId);

    long countByTenantIdAndFarmId(UUID tenantId, UUID farmId);

    @Query(value = "select animal_id from lot_animals where lot_id = :lotId and left_at is null",
            nativeQuery = true)
    List<UUID> findAnimalIdsInLot(@Param("lotId") UUID lotId);
}
