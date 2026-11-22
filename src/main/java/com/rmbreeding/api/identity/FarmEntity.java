package com.rmbreeding.api.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "farms")
class FarmEntity {

    @Id
    UUID id;

    UUID tenantId;

    String name;

    String code;

    @JdbcTypeCode(SqlTypes.CHAR)
    String country;

    @JdbcTypeCode(SqlTypes.CHAR)
    String currency;

    String timezone;

    String address;

    Instant createdAt;

    Instant updatedAt;

    Instant archivedAt;
}
