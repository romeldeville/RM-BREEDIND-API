package com.rmbreeding.api.feeding;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feeds")
class FeedEntity {

    @Id
    UUID id;

    UUID tenantId;

    String name;

    String type;

    String unit;

    BigDecimal price;

    String supplier;

    Instant archivedAt;
}
