package com.rmbreeding.api.alerts;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "alerts")
class AlertEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID farmId;

    String type;

    String priority;

    String title;

    String message;

    LocalDate dueDate;

    String entityType;

    UUID entityId;

    String status;

    String postponeReason;

    String assignedRole;

    String dedupKey;

    Instant createdAt;

    Instant closedAt;

    UUID closedBy;
}
