package com.rmbreeding.api.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
class CustomerEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID farmId;

    String name;

    String phone;

    @Column(columnDefinition = "citext")
    String email;

    String address;

    BigDecimal balance;

    Instant archivedAt;
}
