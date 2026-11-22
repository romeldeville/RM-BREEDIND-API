package com.rmbreeding.api.feeding;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feed_stocks")
class FeedStockEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID feedId;

    BigDecimal quantity;

    BigDecimal averagePrice;

    Instant updatedAt;
}
