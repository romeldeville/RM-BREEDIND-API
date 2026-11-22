package com.rmbreeding.api.sales;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sale_lines")
class SaleLineEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID saleId;

    String itemType;

    UUID animalId;

    UUID lotId;

    String description;

    BigDecimal quantity;

    BigDecimal unitPrice;

    BigDecimal amount;
}
