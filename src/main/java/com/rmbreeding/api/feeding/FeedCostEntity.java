package com.rmbreeding.api.feeding;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "feed_costs")
class FeedCostEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID lotId;

    UUID animalId;

    BigDecimal amount;

    LocalDate periodStart;

    LocalDate periodEnd;
}
