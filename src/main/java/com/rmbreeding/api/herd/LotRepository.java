package com.rmbreeding.api.herd;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface LotRepository extends JpaRepository<LotEntity, UUID>,
        JpaSpecificationExecutor<LotEntity> {

    Optional<LotEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
