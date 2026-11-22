package com.rmbreeding.api.files;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExportJobRepository extends JpaRepository<ExportJobEntity, UUID> {

    Optional<ExportJobEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ExportJobEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<ExportJobEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    List<ExportJobEntity> findByTenantIdAndCompletedAtBefore(UUID tenantId, Instant cutoff);
}
