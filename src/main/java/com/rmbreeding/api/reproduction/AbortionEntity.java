package com.rmbreeding.api.reproduction;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "abortions")
class AbortionEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID cycleId;

    Instant occurredAt;

    String stage;

    String suspectedCause;
}
