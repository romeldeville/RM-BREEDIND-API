package com.rmbreeding.api.health;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "treatment_administrations")
class TreatmentAdministrationEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID treatmentId;

    Instant administeredAt;

    String dose;

    UUID actorId;
}
