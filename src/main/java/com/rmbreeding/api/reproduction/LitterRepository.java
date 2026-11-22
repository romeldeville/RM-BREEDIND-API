package com.rmbreeding.api.reproduction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LitterRepository extends JpaRepository<LitterEntity, UUID> {

    List<LitterEntity> findByTenantIdAndBirthIdOrderByBornAliveDesc(UUID tenantId, UUID birthId);

    Optional<LitterEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
