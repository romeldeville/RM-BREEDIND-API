package com.rmbreeding.api.production;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "egg_movements")
class EggMovementEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID farmId;

    UUID lotId;

    String type;

    int quantity;

    Instant occurredAt;

    String reason;
}
