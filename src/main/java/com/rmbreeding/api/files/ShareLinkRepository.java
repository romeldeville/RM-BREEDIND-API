package com.rmbreeding.api.files;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShareLinkRepository extends JpaRepository<ShareLinkEntity, UUID> {

    Optional<ShareLinkEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ShareLinkEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<ShareLinkEntity> findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID tenantId, String entityType, UUID entityId);
}
