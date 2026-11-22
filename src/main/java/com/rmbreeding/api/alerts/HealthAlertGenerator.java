package com.rmbreeding.api.alerts;

import com.rmbreeding.api.health.HealthDtos;
import com.rmbreeding.api.health.HealthService;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

// P11-03 Health alert generators
@Component
class HealthAlertGenerator implements AlertGenerator {

    private final HealthService healthService;
    private final AlertsService alertsService;

    HealthAlertGenerator(HealthService healthService, AlertsService alertsService) {
        this.healthService = healthService;
        this.alertsService = alertsService;
    }

    @Override
    public void generate(LocalDate today) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID farmId = alertsService.requireDefaultFarmId();
        withdrawals(tenantId, farmId, today);
        quarantines(tenantId, farmId, today);
    }

    private void withdrawals(UUID tenantId, UUID farmId, LocalDate today) {
        for (HealthDtos.WithdrawalResponse withdrawal : healthService.listWithdrawals("ACTIVE")) {
            if (withdrawal.startDate() != null && !withdrawal.startDate().isAfter(today)) {
                alertsService.raise(tenantId, farmId, "WITHDRAWAL_START", "CRITICAL",
                        "Délai d'attente en cours",
                        "Délai d'attente " + withdrawal.type() + " démarré le " + withdrawal.startDate(),
                        withdrawal.startDate(), "withdrawal", withdrawal.id());
            }
            if (withdrawal.endDate() != null && !withdrawal.endDate().isAfter(today)) {
                alertsService.raise(tenantId, farmId, "WITHDRAWAL_END", "CRITICAL",
                        "Fin de délai d'attente",
                        "Délai d'attente " + withdrawal.type() + " terminé le " + withdrawal.endDate(),
                        withdrawal.endDate(), "withdrawal", withdrawal.id());
            }
        }
    }

    private void quarantines(UUID tenantId, UUID farmId, LocalDate today) {
        for (HealthDtos.QuarantineResponse quarantine : healthService.listQuarantines("ACTIVE")) {
            if (quarantine.endDate() != null && !quarantine.endDate().isAfter(today)) {
                alertsService.raise(tenantId, farmId, "QUARANTINE_END", "INFO",
                        "Fin de quarantaine",
                        "La quarantaine se termine le " + quarantine.endDate(),
                        quarantine.endDate(), "quarantine", quarantine.id());
            }
        }
    }
}
