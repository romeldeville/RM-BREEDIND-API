package com.rmbreeding.api.sync;

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
@Table(name = "sync_projections")
class SyncProjectionEntity {

    @Id
    UUID id;

    UUID tenantId;

    String aggregateType;

    UUID aggregateId;

    UUID eventId;

    long serverSeq;

    String eventType;

    Instant occurredAt;

    Instant updatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    Map<String, Object> payload = new HashMap<>();
}
