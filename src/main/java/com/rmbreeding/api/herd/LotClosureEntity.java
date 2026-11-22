package com.rmbreeding.api.herd;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lot_closures")
class LotClosureEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID lotId;

    BigDecimal mortalityRate;

    BigDecimal feedConversion;

    BigDecimal gmq;

    BigDecimal cost;

    BigDecimal revenue;

    BigDecimal margin;

    Instant closedAt;
}
