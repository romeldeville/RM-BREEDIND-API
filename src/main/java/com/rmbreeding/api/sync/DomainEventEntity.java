package com.rmbreeding.api.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "domain_events")
class DomainEventEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID farmId;

    String aggregateType;

    UUID aggregateId;

    String eventType;

    int eventVersion;

    Instant occurredAt;

    Instant recordedAt;

    UUID actorId;

    String clientId;

    String idempotencyKey;

    @Generated(event = EventType.INSERT)
    @Column(name = "server_seq", insertable = false, updatable = false)
    Long serverSeq;

    @JdbcTypeCode(SqlTypes.JSON)
    Map<String, Object> payload = new HashMap<>();

    UUID correctsEventId;

    @JdbcTypeCode(SqlTypes.JSON)
    Map<String, Object> metadata = new HashMap<>();
}
