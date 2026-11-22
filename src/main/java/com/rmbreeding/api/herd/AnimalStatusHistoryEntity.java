package com.rmbreeding.api.herd;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "animal_status_history")
class AnimalStatusHistoryEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID animalId;

    String status;

    Instant changedAt;

    String reason;

    UUID actorId;
}
