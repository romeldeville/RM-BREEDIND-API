package com.rmbreeding.api.finance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cost_allocations")
class CostAllocationEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID lotId;

    String sourceType;

    UUID sourceId;

    String period;

    BigDecimal amount;

    String basis;

    Instant allocatedAt;
}
