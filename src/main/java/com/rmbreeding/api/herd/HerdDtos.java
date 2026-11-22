package com.rmbreeding.api.herd;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class HerdDtos {

    private HerdDtos() {
    }

    public record AnimalCreateRequest(
            @NotNull UUID farmId,
            @NotNull UUID speciesId,
            UUID breedId,
            UUID penId,
            @NotBlank @Size(max = 80) String identifier,
            @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "MALE|FEMALE|UNKNOWN") String sex,
            LocalDate birthDate,
            Boolean birthEstimated,
            @Size(max = 40) String origin,
            UUID motherId,
            UUID fatherId,
            @DecimalMin("0.0") BigDecimal weightKg,
            LocalDate acquiredAt) {
    }

    public record AnimalPatchRequest(
            @Size(max = 80) String identifier,
            @Size(max = 120) String name,
            @Pattern(regexp = "MALE|FEMALE|UNKNOWN") String sex,
            UUID breedId,
            UUID penId,
            LocalDate birthDate,
            Boolean birthEstimated,
            @Size(max = 40) String origin,
            UUID motherId,
            UUID fatherId,
            @DecimalMin("0.0") BigDecimal weightKg,
            LocalDate acquiredAt) {
    }

    public record AnimalResponse(
            UUID id,
            UUID farmId,
            UUID speciesId,
            UUID breedId,
            UUID penId,
            String identifier,
            String name,
            String sex,
            LocalDate birthDate,
            boolean birthEstimated,
            String origin,
            String status,
            UUID motherId,
            UUID fatherId,
            String photoKey,
            BigDecimal weightKg,
            LocalDate acquiredAt,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt,
            int version) {
    }

    public record AnimalStatusRequest(
            @NotBlank @Pattern(regexp = "ACTIVE|QUARANTINED|SOLD|DEAD|SLAUGHTERED|TRANSFERRED|ARCHIVED")
            String status,
            @Size(max = 500) String reason,
            Instant occurredAt) {
    }

    public record AnimalStatusResponse(
            UUID id,
            UUID animalId,
            String status,
            Instant changedAt,
            String reason,
            UUID actorId) {
    }
}
