package com.rmbreeding.api.health;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface HealthProductRepository extends JpaRepository<HealthProductEntity, UUID>,
        JpaSpecificationExecutor<HealthProductEntity> {

    Optional<HealthProductEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
