package com.rmbreeding.api.feeding;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "feed_receipts")
class FeedReceiptEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID feedId;

    BigDecimal quantity;

    BigDecimal unitPrice;

    Instant receivedAt;

    String supplier;

    LocalDate expiryDate;

    String notes;
}
