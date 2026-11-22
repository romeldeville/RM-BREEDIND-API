package com.rmbreeding.api.referential;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "species")
class SpeciesEntity {

    @Id
    UUID id;

    UUID tenantId;

    String name;

    String code;

    String productionType;

    String trackingMode;

    Integer gestationDays;

    Integer heatCycleDays;

    Integer weaningAgeDays;

    Integer incubationDays;

    @JdbcTypeCode(SqlTypes.JSON)
    Map<String, Object> settings = new HashMap<>();

    Instant archivedAt;

    Instant createdAt;

    Instant updatedAt;
}
