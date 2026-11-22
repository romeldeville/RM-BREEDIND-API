package com.rmbreeding.api.herd;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LotMovementRepository extends JpaRepository<LotMovementEntity, UUID> {

    List<LotMovementEntity> findByTenantIdAndLotIdOrderByOccurredAtDesc(UUID tenantId, UUID lotId);
}
