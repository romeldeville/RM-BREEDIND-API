package com.rmbreeding.api.production;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WeighingRepository extends JpaRepository<WeighingEntity, UUID> {

    List<WeighingEntity> findByTenantIdAndAnimalIdOrderByWeighedAtAsc(UUID tenantId, UUID animalId);

    List<WeighingEntity> findByTenantIdAndLotIdOrderByWeighedAtAsc(UUID tenantId, UUID lotId);
}
