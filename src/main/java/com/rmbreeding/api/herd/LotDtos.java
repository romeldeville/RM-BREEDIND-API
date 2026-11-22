package com.rmbreeding.api.herd;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class LotDtos {

    private LotDtos() {
    }

    public record LotCreateRequest(
            @NotNull UUID farmId,
            @NotNull UUID speciesId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 40) String code,
            @Size(max = 20) String productionType,
            LocalDate entryDate,
            @NotNull @Min(0) Integer initialCount,
            @DecimalMin("0.0") BigDecimal unitCost) {
    }

    public record LotPatchRequest(
            @Size(max = 120) String name,
            @Size(max = 40) String code,
            @Size(max = 20) String productionType,
            LocalDate entryDate,
            @DecimalMin("0.0") BigDecimal unitCost) {
    }

    public record LotResponse(
            UUID id,
            UUID farmId,
            UUID speciesId,
            String name,
            String code,
            String productionType,
            String status,
            LocalDate entryDate,
            int initialCount,
            int currentCount,
            String origin,
            BigDecimal unitCost,
            Instant closedAt,
            int version,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record LotMovementRequest(
            @NotBlank @Pattern(regexp = "ENTRY|EXIT|MORTALITY|SALE|TRANSFER|ADJUSTMENT|INDIVIDUALIZATION")
            String type,
            @NotNull @Min(1) Integer quantity,
            @NotNull Instant occurredAt,
            @Size(max = 500) String reason) {
    }

    public record LotMovementResponse(
            UUID id,
            UUID lotId,
            String type,
            int quantity,
            Instant occurredAt,
            String reason,
            UUID actorId,
            Instant createdAt) {
    }

    public record LotClosureResponse(
            UUID lotId,
            BigDecimal mortalityRate,
            BigDecimal feedConversion,
            BigDecimal gmq,
            BigDecimal cost,
            BigDecimal revenue,
            BigDecimal margin,
            Instant closedAt) {
    }

    public record LotSplitRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull @Min(1) Integer quantity) {
    }

    public record LotMergeRequest(
            @NotNull UUID sourceLotId) {
    }

    public record IndividualizeRequest(
            @NotBlank @Size(max = 80) String identifier,
            @NotBlank @Pattern(regexp = "MALE|FEMALE|UNKNOWN") String sex,
            LocalDate birthDate) {
    }
}
