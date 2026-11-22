package com.rmbreeding.api.reproduction;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "weanings")
class WeaningEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID litterId;

    Instant weanedAt;

    int count;

    BigDecimal averageWeight;
}
