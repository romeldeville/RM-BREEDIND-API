package com.rmbreeding.api.reproduction;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "pregnancy_diagnoses")
class PregnancyDiagnosisEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID cycleId;

    Instant diagnosedAt;

    String result;

    LocalDate nextDiagnosisAt;

    String notes;
}
