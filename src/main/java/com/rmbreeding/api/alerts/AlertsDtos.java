package com.rmbreeding.api.alerts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AlertsDtos {

    private AlertsDtos() {
    }

    public record AlertResponse(
            UUID id,
            UUID farmId,
            String type,
            String priority,
            String title,
            String message,
            LocalDate dueDate,
            String status,
            String entityType,
            UUID entityId,
            String assignedRole,
            String postponeReason,
            Instant createdAt,
            Instant closedAt) {
    }

    public record TaskResponse(
            UUID id,
            UUID farmId,
            String title,
            String description,
            LocalDate dueDate,
            UUID assignedTo,
            String recurrence,
            String status,
            Instant createdAt,
            Instant completedAt) {
    }

    public record TodayResponse(
            List<AlertResponse> alerts,
            List<TaskResponse> tasks) {
    }

    public record PostponeRequest(
            @NotNull LocalDate dueDate,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record CancelRequest(
            @NotBlank @Size(max = 500) String reason) {
    }

    public record TaskCreateRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 1000) String description,
            LocalDate dueDate,
            UUID assignedTo,
            @Size(max = 40) String recurrence) {
    }

    public record AutoCloseRequest(
            @NotBlank @Size(max = 40) String entityType,
            @NotNull UUID entityId) {
    }
}
