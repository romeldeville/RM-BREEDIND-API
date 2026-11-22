package com.rmbreeding.api.referential;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "feeding_plans")
class FeedingPlanEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID speciesId;

    String name;

    @JdbcTypeCode(SqlTypes.JSON)
    List<Map<String, Object>> stages = new ArrayList<>();

    boolean active = true;
}
