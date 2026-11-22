package com.rmbreeding.api.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
class TenantEntity {

    @Id
    UUID id;

    String name;

    String slug;

    String status;

    String planCode;

    Instant trialEndsAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    String country;

    @JdbcTypeCode(SqlTypes.CHAR)
    String currency;

    Instant createdAt;

    Instant updatedAt;

    Instant archivedAt;

    Instant deleteRequestedAt;

    Instant deleteAfter;
}
