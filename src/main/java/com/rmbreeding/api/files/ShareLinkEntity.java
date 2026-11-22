package com.rmbreeding.api.files;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "share_links")
class ShareLinkEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID attachmentId;

    String bucket;

    String storageKey;

    String filename;

    String mimeType;

    Long sizeBytes;

    String entityType;

    UUID entityId;

    UUID createdBy;

    Instant expiresAt;

    Instant revokedAt;

    int accessCount;

    Instant lastAccessedAt;

    Instant createdAt;
}
