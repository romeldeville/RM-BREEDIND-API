package com.rmbreeding.api.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SalesDtos {

    private SalesDtos() {
    }

    public record CustomerCreateRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 32) String phone,
            @Email @Size(max = 200) String email,
            @Size(max = 500) String address) {
    }

    public record CustomerPatchRequest(
            @Size(max = 160) String name,
            @Size(max = 32) String phone,
            @Email @Size(max = 200) String email,
            @Size(max = 500) String address) {
    }

    public record CustomerResponse(
            UUID id,
            UUID farmId,
            String name,
            String phone,
            String email,
            String address,
            BigDecimal balance,
            Instant archivedAt) {
    }

    public record SaleLineRequest(
            @Pattern(regexp = "ANIMAL|LOT|EGGS|OTHER") String itemType,
            UUID animalId,
            UUID lotId,
            @Size(max = 200) String description,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
            @NotNull @DecimalMin("0.0") BigDecimal unitPrice) {
    }

    public record SaleCreateRequest(
            UUID customerId,
            @NotBlank @Pattern(regexp = "ANIMAL|LOT|EGGS|OTHER") String type,
            @NotNull Instant soldAt,
            @Size(min = 3, max = 3) String currency,
            @Size(max = 500) String notes,
            @NotEmpty List<@Valid SaleLineRequest> lines) {
    }

    public record SaleLineResponse(
            UUID id,
            String itemType,
            UUID animalId,
            UUID lotId,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount) {
    }

    public record SaleResponse(
            UUID id,
            UUID farmId,
            UUID customerId,
            String number,
            String type,
            String status,
            Instant soldAt,
            BigDecimal totalAmount,
            String currency,
            String paymentStatus,
            String notes,
            Instant cancellationDeadline,
            List<SaleLineResponse> lines) {
    }

    public record SaleCancelRequest(
            @NotBlank @Size(max = 500) String reason) {
    }

    public record PaymentCreateRequest(
            @NotNull @DecimalMin("0.0") BigDecimal amount,
            @NotBlank @Pattern(regexp = "CASH|MOBILE_MONEY|BANK_TRANSFER|CARD|CREDIT") String method,
            @NotNull Instant paidAt,
            @Size(max = 120) String reference) {
    }

    public record PaymentResponse(
            UUID id,
            UUID saleId,
            BigDecimal amount,
            String method,
            String status,
            Instant paidAt,
            String reference) {
    }
}
