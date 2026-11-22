package com.rmbreeding.api.reproduction;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "heats")
class HeatEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID cycleId;

    UUID animalId;

    Instant observedAt;

    String intensity;

    LocalDate nextExpectedAt;

    String notes;
}
