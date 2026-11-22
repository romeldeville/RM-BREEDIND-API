package com.rmbreeding.api.alerts;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tasks")
class TaskEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID farmId;

    String title;

    String description;

    LocalDate dueDate;

    UUID assignedTo;

    String recurrence;

    String status;

    Instant createdAt;

    Instant completedAt;
}
