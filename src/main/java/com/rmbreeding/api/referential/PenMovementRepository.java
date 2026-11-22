package com.rmbreeding.api.referential;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PenMovementRepository extends JpaRepository<PenMovementEntity, UUID> {

    List<PenMovementEntity> findByTenantIdOrderByMovedAtDesc(UUID tenantId);

    List<PenMovementEntity> findByTenantIdAndToPenIdOrderByMovedAtDesc(UUID tenantId, UUID toPenId);
}
