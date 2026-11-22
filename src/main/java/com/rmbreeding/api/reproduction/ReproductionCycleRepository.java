package com.rmbreeding.api.reproduction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ReproductionCycleRepository extends JpaRepository<ReproductionCycleEntity, UUID>,
        JpaSpecificationExecutor<ReproductionCycleEntity> {

    Optional<ReproductionCycleEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ReproductionCycleEntity> findFirstByTenantIdAndFemaleIdAndStatusInOrderByCreatedAtDesc(
            UUID tenantId, UUID femaleId, Collection<String> statuses);

    boolean existsByTenantIdAndFemaleIdAndStatus(UUID tenantId, UUID femaleId, String status);
}
