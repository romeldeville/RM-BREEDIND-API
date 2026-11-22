package com.rmbreeding.api.files;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attachments")
class AttachmentEntity {

    @Id
    UUID id;

    UUID tenantId;

    String entityType;

    UUID entityId;

    String bucket;

    String storageKey;

    String filename;

    String mimeType;

    Long sizeBytes;

    String checksum;

    UUID uploadedBy;

    Instant createdAt;

    Instant confirmedAt;

    Instant deletedAt;
}
