package com.rmbreeding.api.referential;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReferentialDtos {

    private ReferentialDtos() {
    }

    public record SpeciesRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9-]+$") @Size(max = 40) String code,
            @NotBlank @Pattern(regexp = "MEAT|MILK|EGGS|MIXED|OTHER") String productionType,
            @NotBlank @Pattern(regexp = "INDIVIDUAL|LOT|HYBRID") String trackingMode,
            @Min(0) Integer gestationDays,
            @Min(0) Integer heatCycleDays,
            @Min(0) Integer weaningAgeDays,
            @Min(0) Integer incubationDays,
            Map<String, Object> settings) {
    }

    public record SpeciesPatchRequest(
            @Size(max = 120) String name,
            @Pattern(regexp = "MEAT|MILK|EGGS|MIXED|OTHER") String productionType,
            @Pattern(regexp = "INDIVIDUAL|LOT|HYBRID") String trackingMode,
            @Min(0) Integer gestationDays,
            @Min(0) Integer heatCycleDays,
            @Min(0) Integer weaningAgeDays,
            @Min(0) Integer incubationDays,
            Map<String, Object> settings) {
    }

    public record SpeciesResponse(
            UUID id,
            String name,
            String code,
            String productionType,
            String trackingMode,
            Integer gestationDays,
            Integer heatCycleDays,
            Integer weaningAgeDays,
            Integer incubationDays,
            Map<String, Object> settings,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record TemplateRequest(
            @NotBlank @Size(max = 120) String name,
            Map<String, Object> config) {
    }

    public record TemplateResponse(UUID id, UUID speciesId, String name, Map<String, Object> config) {
    }

    public record BreedRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 40) String code) {
    }

    public record BreedResponse(UUID id, UUID speciesId, String name, String code, Instant archivedAt) {
    }

    public record VaccinationPlanRequest(
            @NotBlank @Size(max = 120) String name,
            UUID productId,
            @Min(0) Integer ageDays,
            @Min(0) Integer boosterDays,
            List<Map<String, Object>> schedule,
            Boolean active) {
    }

    public record VaccinationPlanResponse(
            UUID id,
            UUID speciesId,
            String name,
            UUID productId,
            Integer ageDays,
            Integer boosterDays,
            List<Map<String, Object>> schedule,
            boolean active) {
    }

    public record FeedingPlanRequest(
            @NotBlank @Size(max = 120) String name,
            List<Map<String, Object>> stages,
            Boolean active) {
    }

    public record FeedingPlanResponse(
            UUID id,
            UUID speciesId,
            String name,
            List<Map<String, Object>> stages,
            boolean active) {
    }

    public record GrowthCurveRequest(
            @Size(max = 120) String name,
            UUID breedId,
            List<Map<String, Object>> points) {
    }

    public record GrowthCurveResponse(
            UUID id,
            UUID speciesId,
            UUID breedId,
            String name,
            List<Map<String, Object>> points) {
    }

    public record BuildingRequest(
            @NotNull UUID farmId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 40) String code,
            @Size(max = 40) String type) {
    }

    public record BuildingPatchRequest(
            @Size(max = 120) String name,
            @Size(max = 40) String code,
            @Size(max = 40) String type) {
    }

    public record BuildingResponse(UUID id, UUID farmId, String name, String code, String type, Instant archivedAt) {
    }

    public record PenRequest(
            @NotNull UUID buildingId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 40) String code,
            @Size(max = 40) String type,
            @Min(0) int capacity) {
    }

    public record PenPatchRequest(
            @Size(max = 120) String name,
            @Size(max = 40) String code,
            @Size(max = 40) String type,
            @Min(0) Integer capacity) {
    }

    public record PenResponse(
            UUID id,
            UUID farmId,
            UUID buildingId,
            String name,
            String code,
            String type,
            int capacity,
            Instant archivedAt) {
    }

    public record PenOccupancyResponse(UUID penId, int capacity, int occupied) {
    }

    public record PenMovementRequest(
            UUID animalId,
            UUID lotId,
            UUID fromPenId,
            @NotNull UUID toPenId,
            Instant movedAt,
            @Size(max = 500) String reason) {
    }

    public record PenMovementResponse(
            UUID id,
            UUID animalId,
            UUID lotId,
            UUID fromPenId,
            UUID toPenId,
            Instant movedAt,
            String reason) {
    }

    public record GrowthPoint(LocalDate date, double weightKg) {
    }
}
