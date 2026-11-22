package com.rmbreeding.api.feeding;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feed_distributions")
class FeedDistributionEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID feedId;

    UUID lotId;

    UUID animalId;

    BigDecimal quantity;

    Instant distributedAt;

    UUID actorId;

    UUID planId;

    String notes;
}
