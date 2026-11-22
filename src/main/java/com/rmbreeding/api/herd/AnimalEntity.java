package com.rmbreeding.api.herd;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "animals")
class AnimalEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID farmId;

    UUID speciesId;

    UUID breedId;

    UUID penId;

    String identifier;

    String name;

    String sex;

    LocalDate birthDate;

    boolean birthEstimated;

    String origin;

    String status;

    UUID motherId;

    UUID fatherId;

    String photoKey;

    BigDecimal weightKg;

    LocalDate acquiredAt;

    @Version
    int version;

    Instant createdAt;

    Instant updatedAt;

    Instant archivedAt;
}
