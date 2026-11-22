package com.rmbreeding.api.sales;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales")
class SaleEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID farmId;

    UUID customerId;

    String number;

    String type;

    String status;

    Instant soldAt;

    BigDecimal totalAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    String currency;

    String notes;

    Instant cancellationDeadline;
}
