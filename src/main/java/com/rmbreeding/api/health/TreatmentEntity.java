package com.rmbreeding.api.health;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "treatments")
class TreatmentEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID animalId;

    UUID lotId;

    String diagnosis;

    String symptoms;

    UUID productId;

    String dose;

    String route;

    LocalDate startDate;

    Integer durationDays;

    LocalDate endDate;

    String status;

    String notes;
}
