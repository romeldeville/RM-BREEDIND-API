package com.rmbreeding.api.referential;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pen_movements")
class PenMovementEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID farmId;

    UUID animalId;

    UUID lotId;

    UUID fromPenId;

    UUID toPenId;

    Instant movedAt;

    String reason;

    UUID actorId;

    Instant createdAt;
}
