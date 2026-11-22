package com.rmbreeding.api.files;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<AttachmentEntity, UUID> {

    Optional<AttachmentEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<AttachmentEntity> findByTenantIdAndEntityTypeAndEntityIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID tenantId, String entityType, UUID entityId);

    List<AttachmentEntity> findByTenantIdAndConfirmedAtIsNullAndDeletedAtIsNullAndCreatedAtBefore(
            UUID tenantId, Instant cutoff);

    List<AttachmentEntity> findByTenantIdAndDeletedAtBefore(UUID tenantId, Instant cutoff);

    @Query(value = "select id from tenants", nativeQuery = true)
    List<UUID> findAllTenantIds();
}
