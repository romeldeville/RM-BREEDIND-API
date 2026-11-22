package com.rmbreeding.api.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncConflictRepository extends JpaRepository<SyncConflictEntity, UUID> {

    Optional<SyncConflictEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SyncConflictEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<SyncConflictEntity> findByTenantIdAndResolvedAtIsNullOrderByCreatedAtDesc(UUID tenantId);

    long countByTenantIdAndResolvedAtIsNull(UUID tenantId);
}
