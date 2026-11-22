package com.rmbreeding.api.health;

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
import java.util.Map;
import java.util.UUID;

public final class HealthDtos {

    private HealthDtos() {
    }

    public record ProductCreateRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 40) String type,
            List<String> speciesAllowed,
            @Size(max = 80) String dose,
            @Size(max = 40) String route,
            @Min(0) Integer withdrawalMeatDays,
            @Min(0) Integer withdrawalEggsDays,
            @Min(0) Integer withdrawalMilkDays,
            @Min(0) Integer shelfLifeDays,
            @Size(max = 160) String supplier,
            @DecimalMin("0.0") BigDecimal stockQty) {
    }

    public record ProductPatchRequest(
            @Size(max = 160) String name,
            @Size(max = 40) String type,
            List<String> speciesAllowed,
            @Size(max = 80) String dose,
            @Size(max = 40) String route,
            @Min(0) Integer withdrawalMeatDays,
            @Min(0) Integer withdrawalEggsDays,
            @Min(0) Integer withdrawalMilkDays,
            @Min(0) Integer shelfLifeDays,
            @Size(max = 160) String supplier,
            @DecimalMin("0.0") BigDecimal stockQty) {
    }

    public record ProductResponse(
            UUID id,
            String name,
            String type,
            List<String> speciesAllowed,
            String dose,
            String route,
            int withdrawalMeatDays,
            int withdrawalEggsDays,
            int withdrawalMilkDays,
            Integer shelfLifeDays,
            String supplier,
            BigDecimal stockQty,
            Instant archivedAt) {
    }

    public record VaccinationPlanCreateRequest(
            @NotNull UUID speciesId,
            UUID productId,
            @NotBlank @Size(max = 120) String name,
            @Min(0) Integer ageDays,
            @Min(0) Integer boosterDays,
            List<Map<String, Object>> schedule,
            Boolean active) {
    }

    public record VaccinationCreateRequest(
            UUID animalId,
            UUID lotId,
            @NotNull UUID productId,
            UUID planId,
            @Size(max = 80) String vaccineLot,
            @Size(max = 80) String dose,
            @Size(max = 40) String route,
            @Min(1) Integer count,
            @NotNull Instant performedAt,
            UUID vetId,
            @Size(max = 1000) String notes) {
    }

    public record VaccinationResponse(
            UUID id,
            UUID animalId,
            UUID lotId,
            UUID productId,
            UUID planId,
            String vaccineLot,
            String dose,
            String route,
            int count,
            Instant performedAt,
            UUID vetId,
            String notes) {
    }

    public record TreatmentCreateRequest(
            UUID animalId,
            UUID lotId,
            @NotNull UUID productId,
            @Size(max = 200) String diagnosis,
            @Size(max = 1000) String symptoms,
            @Size(max = 80) String dose,
            @Size(max = 40) String route,
            @NotNull LocalDate startDate,
            @Min(0) Integer durationDays,
            @Size(max = 1000) String notes) {
    }

    public record TreatmentPatchRequest(
            @Pattern(regexp = "ACTIVE|COMPLETED|CANCELLED") String status,
            LocalDate endDate,
            @Size(max = 1000) String notes) {
    }

    public record AdministrationCreateRequest(
            @NotNull Instant administeredAt,
            @Size(max = 80) String dose) {
    }

    public record AdministrationResponse(
            UUID id,
            UUID treatmentId,
            Instant administeredAt,
            String dose,
            UUID actorId) {
    }

    public record TreatmentResponse(
            UUID id,
            UUID animalId,
            UUID lotId,
            UUID productId,
            String diagnosis,
            String symptoms,
            String dose,
            String route,
            LocalDate startDate,
            Integer durationDays,
            LocalDate endDate,
            String status,
            String notes,
            LocalDate withdrawalEndDate,
            List<AdministrationResponse> administrations) {
    }

    public record WithdrawalResponse(
            UUID id,
            UUID treatmentId,
            UUID animalId,
            UUID lotId,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            String status) {
    }

    public record HealthEventCreateRequest(
            UUID animalId,
            UUID lotId,
            @Size(max = 1000) String symptoms,
            @Min(1) Integer affectedCount,
            @Size(max = 200) String diagnosis,
            @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String severity,
            @NotNull Instant occurredAt) {
    }

    public record HealthEventResponse(
            UUID id,
            UUID animalId,
            UUID lotId,
            String symptoms,
            Integer affectedCount,
            String diagnosis,
            String severity,
            Instant occurredAt) {
    }

    public record MortalityCreateRequest(
            UUID animalId,
            UUID lotId,
            @Min(1) Integer count,
            @Size(max = 200) String cause,
            @NotNull Instant occurredAt) {
    }

    public record MortalityResponse(
            UUID id,
            UUID animalId,
            UUID lotId,
            int count,
            String cause,
            Instant occurredAt,
            UUID actorId) {
    }

    public record QuarantineCreateRequest(
            UUID animalId,
            UUID lotId,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @Size(max = 1000) String reason) {
    }

    public record QuarantineResponse(
            UUID id,
            UUID animalId,
            UUID lotId,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            String status,
            UUID releasedBy) {
    }

    public record HealthBookResponse(
            UUID animalId,
            List<VaccinationResponse> vaccinations,
            List<TreatmentResponse> treatments,
            List<WithdrawalResponse> withdrawals,
            List<MortalityResponse> mortalities,
            List<QuarantineResponse> quarantines) {
    }

    public record SaleEligibilityResponse(
            boolean allowed,
            List<WithdrawalResponse> blockingWithdrawals) {
    }
}
