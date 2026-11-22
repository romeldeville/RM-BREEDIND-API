package com.rmbreeding.api.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FarmRepository extends JpaRepository<FarmEntity, UUID> {

    List<FarmEntity> findByTenantIdOrderByName(UUID tenantId);

    Optional<FarmEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);
}
