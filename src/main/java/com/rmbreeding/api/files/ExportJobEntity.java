package com.rmbreeding.api.files;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "export_jobs")
class ExportJobEntity {

    @Id
    UUID id;

    UUID tenantId;

    String type;

    String status;

    @JdbcTypeCode(SqlTypes.JSON)
    Map<String, Object> params = new HashMap<>();

    String bucket;

    String storageKey;

    String filename;

    String mimeType;

    Long sizeBytes;

    Integer rowCount;

    String errorMessage;

    UUID requestedBy;

    Instant createdAt;

    Instant startedAt;

    Instant completedAt;
}
