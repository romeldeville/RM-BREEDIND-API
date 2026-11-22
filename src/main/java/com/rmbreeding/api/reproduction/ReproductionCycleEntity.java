package com.rmbreeding.api.reproduction;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reproduction_cycles")
class ReproductionCycleEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID farmId;

    UUID femaleId;

    String status;

    int parity;

    LocalDate startDate;

    LocalDate endDate;

    Instant createdAt;

    Instant updatedAt;
}
