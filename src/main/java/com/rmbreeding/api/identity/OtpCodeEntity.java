package com.rmbreeding.api.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_codes")
class OtpCodeEntity {

    @Id
    UUID id;

    UUID userId;

    String destination;

    String channel;

    String codeHash;

    Instant expiresAt;

    Instant consumedAt;

    int attempts;

    Instant createdAt;
}
