package com.rmbreeding.api.referential;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "species_templates")
class SpeciesTemplateEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID speciesId;

    String name;

    @JdbcTypeCode(SqlTypes.JSON)
    Map<String, Object> config = new HashMap<>();
}
