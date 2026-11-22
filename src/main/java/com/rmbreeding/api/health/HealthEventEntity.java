package com.rmbreeding.api.health;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "health_events")
class HealthEventEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID animalId;

    UUID lotId;

    String symptoms;

    Integer affectedCount;

    String diagnosis;

    String severity;

    Instant occurredAt;
}
