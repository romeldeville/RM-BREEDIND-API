package com.rmbreeding.api.health;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vaccinations")
class VaccinationEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID animalId;

    UUID lotId;

    UUID productId;

    UUID planId;

    String vaccineLot;

    String dose;

    String route;

    int count;

    Instant performedAt;

    UUID operatorId;

    UUID vetId;

    String notes;
}
