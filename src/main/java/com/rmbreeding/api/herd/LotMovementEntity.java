package com.rmbreeding.api.herd;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lot_movements")
class LotMovementEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID lotId;

    String type;

    int quantity;

    Instant occurredAt;

    String reason;

    UUID actorId;

    UUID sourceEventId;

    Instant createdAt;
}
