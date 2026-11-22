package com.rmbreeding.api.referential;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pens")
class PenEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID farmId;

    UUID buildingId;

    String name;

    String code;

    String type;

    int capacity;

    Instant archivedAt;

    Instant createdAt;

    Instant updatedAt;
}
