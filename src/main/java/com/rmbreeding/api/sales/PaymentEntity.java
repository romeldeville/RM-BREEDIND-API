package com.rmbreeding.api.sales;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
class PaymentEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID saleId;

    BigDecimal amount;

    String method;

    String status;

    Instant paidAt;

    String reference;
}
