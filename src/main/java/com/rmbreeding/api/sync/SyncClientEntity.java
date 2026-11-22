package com.rmbreeding.api.sync;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_clients")
class SyncClientEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID userId;

    String deviceId;

    long lastCursor;

    Instant lastSeenAt;
}
