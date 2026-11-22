package com.rmbreeding.api.feeding;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class FeedingDtos {

    private FeedingDtos() {
    }

    public record FeedCreateRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 40) String type,
            @NotBlank @Size(max = 20) String unit,
            @DecimalMin("0.0") BigDecimal price,
            @Size(max = 160) String supplier) {
    }

    public record FeedPatchRequest(
            @Size(max = 160) String name,
            @Size(max = 40) String type,
            @Size(max = 20) String unit,
            @DecimalMin("0.0") BigDecimal price,
            @Size(max = 160) String supplier) {
    }

    public record FeedResponse(
            UUID id,
            String name,
            String type,
            String unit,
            BigDecimal price,
            String supplier,
            Instant archivedAt) {
    }

    public record FeedReceiptCreateRequest(
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
            @DecimalMin("0.0") BigDecimal unitPrice,
            @NotNull Instant receivedAt,
            @Size(max = 160) String supplier,
            LocalDate expiryDate,
            @Size(max = 500) String notes) {
    }

    public record FeedReceiptResponse(
            UUID id,
            UUID feedId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            Instant receivedAt,
            String supplier,
            LocalDate expiryDate,
            String notes) {
    }

    public record FeedDistributionCreateRequest(
            @NotNull UUID feedId,
            UUID lotId,
            UUID animalId,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
            @NotNull Instant distributedAt,
            UUID planId,
            @Size(max = 500) String notes) {
    }

    public record FeedDistributionResponse(
            UUID id,
            UUID feedId,
            UUID lotId,
            UUID animalId,
            BigDecimal quantity,
            Instant distributedAt,
            UUID actorId,
            UUID planId,
            String notes) {
    }

    public record FeedStockResponse(
            UUID feedId,
            String feedName,
            String unit,
            BigDecimal quantity,
            BigDecimal averagePrice,
            Instant updatedAt) {
    }

    public record FeedCostComputeRequest(
            UUID lotId,
            UUID animalId,
            @NotNull Instant from,
            @NotNull Instant to) {
    }

    public record FeedCostResponse(
            UUID id,
            UUID lotId,
            UUID animalId,
            BigDecimal amount,
            LocalDate periodStart,
            LocalDate periodEnd) {
    }

    public record LowStockAlertResponse(
            UUID feedId,
            String feedName,
            String unit,
            BigDecimal quantity,
            BigDecimal threshold) {
    }
}
