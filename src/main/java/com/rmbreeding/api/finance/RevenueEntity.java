package com.rmbreeding.api.finance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "revenues")
class RevenueEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID farmId;

    String category;

    BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    String currency;

    LocalDate receivedAt;

    String description;

    UUID saleId;
}
