package com.rmbreeding.api.reproduction;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "litters")
class LitterEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID birthId;

    UUID lotId;

    String name;

    int bornAlive;

    Integer weanedCount;

    LocalDate weaningDate;
}
