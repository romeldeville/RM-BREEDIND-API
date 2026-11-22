package com.rmbreeding.api.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
class AuditLogEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID actorId;

    String action;

    String entityType;

    UUID entityId;

    Instant occurredAt;
}
