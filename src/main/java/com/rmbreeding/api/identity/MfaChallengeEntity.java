package com.rmbreeding.api.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mfa_challenges")
class MfaChallengeEntity {

    @Id
    UUID id;

    UUID userId;

    String codeHash;

    Instant expiresAt;

    Instant consumedAt;

    Instant createdAt;
}
