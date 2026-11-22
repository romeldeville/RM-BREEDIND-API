package com.rmbreeding.api.reproduction;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "births")
class BirthEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID cycleId;

    UUID femaleId;

    Instant occurredAt;

    int bornAlive;

    int stillborn;

    int mummified;

    BigDecimal litterWeight;

    boolean assistance;

    String complications;

    UUID operatorId;
}
