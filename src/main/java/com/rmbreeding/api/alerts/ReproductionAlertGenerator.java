package com.rmbreeding.api.alerts;

import com.rmbreeding.api.reproduction.ReproductionDtos;
import com.rmbreeding.api.reproduction.ReproductionService;
import com.rmbreeding.api.shared.PageResponse;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

// P11-02 Reproduction alert generators
@Component
class ReproductionAlertGenerator implements AlertGenerator {

    private static final Set<String> CLOSED_CYCLE_STATUSES = Set.of("ABORTED", "BIRTHED");
    private static final int[] FARROWING_OFFSETS = {-7, 0, 2};

    private final ReproductionService reproductionService;
    private final AlertsService alertsService;

    ReproductionAlertGenerator(ReproductionService reproductionService, AlertsService alertsService) {
        this.reproductionService = reproductionService;
        this.alertsService = alertsService;
    }

    @Override
    public void generate(LocalDate today) {
        UUID tenantId = TenantContext.requireTenantId();
        PageResponse<ReproductionDtos.CycleResponse> cycles =
                reproductionService.listCycles(null, null, 100, null);
        for (ReproductionDtos.CycleResponse cycle : cycles.items()) {
            if (CLOSED_CYCLE_STATUSES.contains(cycle.status())) {
                continue;
            }
            ReproductionDtos.CycleDetailResponse detail = reproductionService.getCycle(cycle.id());
            diagnosisDue(tenantId, cycle, detail, today);
            farrowingDue(tenantId, cycle, detail, today);
            weaningDue(tenantId, cycle, detail, today);
            heatReturn(tenantId, cycle, detail, today);
        }
    }

    private void diagnosisDue(UUID tenantId, ReproductionDtos.CycleResponse cycle,
            ReproductionDtos.CycleDetailResponse detail, LocalDate today) {
        LocalDate due = cycle.expectedDiagnosisDate();
        if (due == null || due.isAfter(today) || !detail.diagnoses().isEmpty()) {
            return;
        }
        alertsService.raise(tenantId, cycle.farmId(), "PREGNANCY_DIAGNOSIS_DUE", "IMPORTANT",
                "Diagnostic de gestation à réaliser",
                "Le diagnostic de gestation est attendu pour la femelle " + cycle.femaleId(),
                due, "cycle", cycle.id());
    }

    private void farrowingDue(UUID tenantId, ReproductionDtos.CycleResponse cycle,
            ReproductionDtos.CycleDetailResponse detail, LocalDate today) {
        LocalDate expected = cycle.expectedBirthDate();
        if (expected == null || !detail.births().isEmpty()) {
            return;
        }
        for (int offset : FARROWING_OFFSETS) {
            LocalDate due = expected.plusDays(offset);
            if (due.isAfter(today)) {
                continue;
            }
            alertsService.raise(tenantId, cycle.farmId(), "FARROWING_DUE", "CRITICAL",
                    "Mise bas prévue",
                    "Mise bas prévue le " + expected + " pour la femelle " + cycle.femaleId(),
                    due, "cycle", cycle.id());
        }
    }

    private void weaningDue(UUID tenantId, ReproductionDtos.CycleResponse cycle,
            ReproductionDtos.CycleDetailResponse detail, LocalDate today) {
        for (ReproductionDtos.LitterResponse litter : detail.litters()) {
            LocalDate due = litter.weaningDate();
            if (due == null || due.isAfter(today) || litter.weanedCount() != null) {
                continue;
            }
            alertsService.raise(tenantId, cycle.farmId(), "WEANING_DUE", "IMPORTANT",
                    "Sevrage prévu",
                    "Le sevrage de la portée est prévu le " + due,
                    due, "litter", litter.id());
        }
    }

    private void heatReturn(UUID tenantId, ReproductionDtos.CycleResponse cycle,
            ReproductionDtos.CycleDetailResponse detail, LocalDate today) {
        for (ReproductionDtos.HeatResponse heat : detail.heats()) {
            LocalDate due = heat.nextExpectedAt();
            if (due == null || due.isAfter(today) || matedAfter(detail, heat.observedAt())) {
                continue;
            }
            alertsService.raise(tenantId, cycle.farmId(), "HEAT_RETURN", "INFO",
                    "Retour en chaleur attendu",
                    "Un retour en chaleur est attendu pour la femelle " + cycle.femaleId(),
                    due, "heat", heat.id());
        }
    }

    private boolean matedAfter(ReproductionDtos.CycleDetailResponse detail, Instant reference) {
        return detail.matings().stream()
                .anyMatch(mating -> mating.matedAt() != null && mating.matedAt().isAfter(reference));
    }
}
