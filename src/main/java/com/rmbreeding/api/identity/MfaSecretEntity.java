package com.rmbreeding.api.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mfa_secrets")
class MfaSecretEntity {

    @Id
    UUID id;

    UUID userId;

    String secret;

    boolean enabled;

    Instant createdAt;

    Instant updatedAt;
}
