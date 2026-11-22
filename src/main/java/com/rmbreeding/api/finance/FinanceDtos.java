package com.rmbreeding.api.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class FinanceDtos {

    private FinanceDtos() {
    }

    public record ExpenseCreateRequest(
            @NotBlank @Size(max = 60) String category,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
            @Size(min = 3, max = 3) String currency,
            @NotNull LocalDate incurredAt,
            @Size(max = 500) String description,
            @Size(max = 160) String supplier,
            UUID lotId) {
    }

    public record ExpenseResponse(
            UUID id,
            UUID farmId,
            String category,
            BigDecimal amount,
            String currency,
            LocalDate incurredAt,
            String description,
            String supplier) {
    }

    public record RevenueCreateRequest(
            @NotBlank @Size(max = 60) String category,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
            @Size(min = 3, max = 3) String currency,
            @NotNull LocalDate receivedAt,
            @Size(max = 500) String description) {
    }

    public record RevenueResponse(
            UUID id,
            UUID farmId,
            String category,
            BigDecimal amount,
            String currency,
            LocalDate receivedAt,
            String description) {
    }

    public record CostAllocationCreateRequest(
            @NotBlank @Pattern(regexp = "EXPENSE|FEED|MANUAL|OVERHEAD") String sourceType,
            UUID sourceId,
            @Size(max = 7) String period,
            @NotNull @DecimalMin("0.0") BigDecimal amount,
            @Size(max = 40) String basis) {
    }

    public record CostAllocationResponse(
            UUID id,
            UUID lotId,
            String sourceType,
            UUID sourceId,
            String period,
            BigDecimal amount,
            String basis,
            Instant allocatedAt) {
    }

    public record LotCostResponse(
            UUID lotId,
            BigDecimal feedCost,
            BigDecimal allocatedCost,
            BigDecimal totalCost) {
    }

    public record LotMarginResponse(
            UUID lotId,
            BigDecimal revenue,
            BigDecimal cost,
            BigDecimal margin,
            BigDecimal marginRate) {
    }

    public record FinancialStatementLine(
            String category,
            BigDecimal amount) {
    }

    public record FinancialStatementResponse(
            LocalDate from,
            LocalDate to,
            BigDecimal totalRevenue,
            BigDecimal totalExpense,
            BigDecimal net,
            java.util.List<FinancialStatementLine> revenues,
            java.util.List<FinancialStatementLine> expenses) {
    }
}
