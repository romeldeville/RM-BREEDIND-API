package com.rmbreeding.api.health;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "health_products")
class HealthProductEntity {

    @Id
    UUID id;

    UUID tenantId;

    String name;

    String type;

    @JdbcTypeCode(SqlTypes.ARRAY)
    List<String> speciesAllowed = new ArrayList<>();

    String dose;

    String route;

    int withdrawalMeatDays;

    int withdrawalEggsDays;

    int withdrawalMilkDays;

    Integer shelfLifeDays;

    String supplier;

    BigDecimal stockQty;

    Instant archivedAt;
}
