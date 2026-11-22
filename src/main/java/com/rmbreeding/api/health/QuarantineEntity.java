package com.rmbreeding.api.health;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "quarantines")
class QuarantineEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID animalId;

    UUID lotId;

    LocalDate startDate;

    LocalDate endDate;

    String reason;

    String status;

    UUID releasedBy;
}
