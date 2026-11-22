package com.rmbreeding.api.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity {

    @Id
    UUID id;

    UUID userId;

    UUID tenantId;

    String tokenHash;

    Instant expiresAt;

    Instant revokedAt;

    UUID rotatedFrom;

    UUID supportSessionId;

    Instant createdAt;
}
