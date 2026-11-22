package com.rmbreeding.api.reporting;

import com.rmbreeding.api.alerts.AlertsDtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReportingDtos {

    private ReportingDtos() {
    }

    public record IndicatorResponse(
            String code,
            String label,
            String unit,
            BigDecimal value,
            LocalDate periodStart,
            LocalDate periodEnd) {
    }

    public record DashboardResponse(
            long herdSize,
            long openAlerts,
            long birthsThisMonth,
            BigDecimal averageDailyGain,
            BigDecimal grossMargin,
            List<AlertsDtos.AlertResponse> criticalAlerts) {
    }
}
