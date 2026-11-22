package com.rmbreeding.api.notifications;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "push_tokens")
class PushTokenEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID userId;

    String token;

    String platform;

    Instant createdAt;

    Instant lastSeenAt;
}
