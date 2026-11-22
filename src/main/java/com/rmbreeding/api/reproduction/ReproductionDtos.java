package com.rmbreeding.api.reproduction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReproductionDtos {

    private ReproductionDtos() {
    }

    public record CycleCreateRequest(
            @NotNull UUID femaleId,
            LocalDate startDate) {
    }

    public record HeatCreateRequest(
            @NotNull UUID animalId,
            @NotNull Instant observedAt,
            @Pattern(regexp = "LOW|MEDIUM|HIGH") String intensity,
            @Size(max = 1000) String notes) {
    }

    public record MatingCreateRequest(
            @NotNull UUID femaleId,
            UUID maleId,
            @Size(max = 80) String semenCode,
            @Pattern(regexp = "NATURAL|AI") String method,
            @NotNull Instant matedAt,
            @Size(max = 1000) String notes) {
    }

    public record DiagnosisCreateRequest(
            @NotNull UUID cycleId,
            @NotNull Instant diagnosedAt,
            @NotBlank @Pattern(regexp = "POSITIVE|NEGATIVE|UNCERTAIN") String result,
            @Size(max = 1000) String notes) {
    }

    public record AbortionCreateRequest(
            @NotNull UUID cycleId,
            @NotNull Instant occurredAt,
            @Size(max = 20) String stage,
            @Size(max = 1000) String suspectedCause) {
    }

    public record BirthCreateRequest(
            @NotNull UUID cycleId,
            @NotNull UUID femaleId,
            @NotNull Instant occurredAt,
            @NotNull @Min(0) Integer bornAlive,
            @Min(0) Integer stillborn,
            @Min(0) Integer mummified,
            @DecimalMin("0.0") BigDecimal litterWeight,
            Boolean assistance,
            @Size(max = 1000) String complications) {
    }

    public record WeaningCreateRequest(
            @NotNull UUID litterId,
            @NotNull Instant weanedAt,
            @NotNull @Min(0) Integer count,
            @DecimalMin("0.0") BigDecimal averageWeight) {
    }

    public record LitterLotCreateRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 40) String code,
            @Size(max = 20) String productionType,
            @DecimalMin("0.0") BigDecimal unitCost) {
    }

    public record CycleResponse(
            UUID id,
            UUID farmId,
            UUID femaleId,
            String status,
            int parity,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate expectedBirthDate,
            LocalDate expectedDiagnosisDate,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CycleDetailResponse(
            CycleResponse cycle,
            List<HeatResponse> heats,
            List<MatingResponse> matings,
            List<DiagnosisResponse> diagnoses,
            List<AbortionResponse> abortions,
            List<BirthResponse> births,
            List<LitterResponse> litters) {
    }

    public record HeatResponse(
            UUID id,
            UUID cycleId,
            UUID animalId,
            Instant observedAt,
            String intensity,
            LocalDate nextExpectedAt,
            String notes) {
    }

    public record MatingResponse(
            UUID id,
            UUID cycleId,
            UUID femaleId,
            UUID maleId,
            String semenCode,
            String method,
            Instant matedAt,
            LocalDate expectedBirthDate,
            LocalDate expectedDiagnosisDate,
            String notes) {
    }

    public record DiagnosisResponse(
            UUID id,
            UUID cycleId,
            Instant diagnosedAt,
            String result,
            LocalDate nextDiagnosisAt,
            String notes) {
    }

    public record AbortionResponse(
            UUID id,
            UUID cycleId,
            Instant occurredAt,
            String stage,
            String suspectedCause) {
    }

    public record BirthResponse(
            UUID id,
            UUID cycleId,
            UUID femaleId,
            Instant occurredAt,
            int bornAlive,
            int stillborn,
            int mummified,
            BigDecimal litterWeight,
            boolean assistance,
            String complications,
            UUID litterId) {
    }

    public record BirthCreationResponse(
            BirthResponse birth,
            UUID litterId,
            boolean retroactiveMatingSuggested,
            LocalDate suggestedMatingDate) {
    }

    public record LitterResponse(
            UUID id,
            UUID birthId,
            UUID lotId,
            String name,
            int bornAlive,
            Integer weanedCount,
            LocalDate weaningDate,
            List<WeaningResponse> weanings) {
    }

    public record WeaningResponse(
            UUID id,
            UUID litterId,
            Instant weanedAt,
            int count,
            BigDecimal averageWeight) {
    }
}
