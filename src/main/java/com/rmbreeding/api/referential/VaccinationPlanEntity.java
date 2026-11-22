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
@Table(name = "vaccination_plans")
class VaccinationPlanEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID speciesId;

    UUID productId;

    String name;

    Integer ageDays;

    Integer boosterDays;

    @JdbcTypeCode(SqlTypes.JSON)
    List<Map<String, Object>> schedule = new ArrayList<>();

    boolean active = true;
}
