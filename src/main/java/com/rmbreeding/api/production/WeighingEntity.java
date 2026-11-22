package com.rmbreeding.api.production;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "weighings")
class WeighingEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID animalId;

    UUID lotId;

    Instant weighedAt;

    BigDecimal weightKg;

    Integer sampleCount;

    BigDecimal averageWeight;
}
