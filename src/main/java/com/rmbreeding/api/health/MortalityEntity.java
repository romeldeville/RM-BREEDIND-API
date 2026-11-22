package com.rmbreeding.api.health;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mortalities")
class MortalityEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID animalId;

    UUID lotId;

    int count;

    String cause;

    Instant occurredAt;

    UUID actorId;
}
