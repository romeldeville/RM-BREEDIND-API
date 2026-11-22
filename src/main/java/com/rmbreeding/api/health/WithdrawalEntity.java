package com.rmbreeding.api.health;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "withdrawals")
class WithdrawalEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID treatmentId;

    UUID animalId;

    UUID lotId;

    String type;

    LocalDate startDate;

    LocalDate endDate;

    String status;
}
