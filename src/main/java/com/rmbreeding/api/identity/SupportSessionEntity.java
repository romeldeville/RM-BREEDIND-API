package com.rmbreeding.api.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "support_sessions")
class SupportSessionEntity {

    @Id
    UUID id;

    UUID platformAdminId;

    UUID tenantId;

    String reason;

    Instant startedAt;

    Instant expiresAt;

    Instant endedAt;
}
