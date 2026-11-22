package com.rmbreeding.api.herd;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "lots")
class LotEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID farmId;

    UUID speciesId;

    String name;

    String code;

    String productionType;

    String status;

    LocalDate entryDate;

    int initialCount;

    int currentCount;

    String origin;

    BigDecimal unitCost;

    Instant closedAt;

    @Version
    int version;

    Instant createdAt;

    Instant updatedAt;
}
