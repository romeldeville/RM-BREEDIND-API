package com.rmbreeding.api.referential;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "buildings")
class BuildingEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID farmId;

    String name;

    String code;

    String type;

    Instant archivedAt;

    Instant createdAt;

    Instant updatedAt;
}
