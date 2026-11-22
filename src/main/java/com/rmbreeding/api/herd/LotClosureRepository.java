package com.rmbreeding.api.herd;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LotClosureRepository extends JpaRepository<LotClosureEntity, UUID> {

    Optional<LotClosureEntity> findByTenantIdAndLotId(UUID tenantId, UUID lotId);
}
