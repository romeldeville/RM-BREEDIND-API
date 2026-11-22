package com.rmbreeding.api.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_verification_tokens")
class EmailVerificationTokenEntity {

    @Id
    UUID id;

    UUID userId;

    String tokenHash;

    Instant expiresAt;

    Instant consumedAt;

    Instant createdAt;
}
