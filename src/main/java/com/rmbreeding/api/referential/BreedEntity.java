package com.rmbreeding.api.referential;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "breeds")
class BreedEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID speciesId;

    String name;

    String code;

    Instant archivedAt;
}
