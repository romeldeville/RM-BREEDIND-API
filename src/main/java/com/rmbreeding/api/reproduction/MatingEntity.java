package com.rmbreeding.api.reproduction;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matings")
class MatingEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID cycleId;

    UUID femaleId;

    UUID maleId;

    String semenCode;

    String method;

    Instant matedAt;

    UUID operatorId;

    UUID referenceMatingId;

    String notes;
}
