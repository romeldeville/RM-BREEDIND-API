package com.rmbreeding.api.production;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProductionDtos {

    private ProductionDtos() {
    }

    public record WeighingCreateRequest(
            UUID animalId,
            UUID lotId,
            @NotNull Instant weighedAt,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal weightKg,
            @Min(1) Integer sampleCount,
            @DecimalMin(value = "0.0", inclusive = false) BigDecimal averageWeight) {
    }

    public record WeighingResponse(
            UUID id,
            UUID animalId,
            UUID lotId,
            Instant weighedAt,
            BigDecimal weightKg,
            Integer sampleCount,
            BigDecimal averageWeight) {
    }

    public record GmqResponse(
            UUID animalId,
            UUID lotId,
            Instant from,
            Instant to,
            long days,
            BigDecimal startWeightKg,
            BigDecimal endWeightKg,
            BigDecimal gmq) {
    }

    public record GrowthPoint(
            Instant weighedAt,
            BigDecimal weightKg,
            Integer ageDays) {
    }

    public record GrowthCurveResponse(
            UUID animalId,
            UUID lotId,
            UUID speciesId,
            List<GrowthPoint> points,
            List<Map<String, Object>> reference) {
    }

    public record EggCollectionCreateRequest(
            UUID lotId,
            @NotNull Instant collectedAt,
            @NotNull @Min(0) Integer collected,
            @Min(0) Integer broken,
            @Min(0) Integer downgraded) {
    }

    public record EggCollectionResponse(
            UUID id,
            UUID farmId,
            UUID lotId,
            Instant collectedAt,
            int collected,
            int broken,
            int downgraded,
            int sellable) {
    }

    public record EggMovementCreateRequest(
            UUID lotId,
            @NotBlank @Pattern(regexp = "SALE|LOSS|ADJUSTMENT|TRANSFER|ENTRY") String type,
            @NotNull @Min(1) Integer quantity,
            @NotNull Instant occurredAt,
            @Size(max = 500) String reason) {
    }

    public record EggMovementResponse(
            UUID id,
            UUID farmId,
            UUID lotId,
            String type,
            int quantity,
            Instant occurredAt,
            String reason) {
    }

    public record EggStockResponse(
            UUID farmId,
            UUID lotId,
            int quantity,
            Instant updatedAt) {
    }

    public record LayingRateResponse(
            UUID lotId,
            Instant from,
            Instant to,
            long days,
            int henCount,
            int totalCollected,
            BigDecimal layingRate) {
    }
}
