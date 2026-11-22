package com.rmbreeding.api.reporting;

import com.rmbreeding.api.alerts.AlertsDtos;
import com.rmbreeding.api.alerts.AlertsService;
import com.rmbreeding.api.identity.FarmService;
import com.rmbreeding.api.identity.IdentityDtos;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {

    private final IndicatorService indicatorService;
    private final AlertsService alertsService;
    private final FarmService farmService;
    private final DashboardSnapshotRepository snapshotRepository;

    public DashboardService(IndicatorService indicatorService,
            AlertsService alertsService,
            FarmService farmService,
            DashboardSnapshotRepository snapshotRepository) {
        this.indicatorService = indicatorService;
        this.alertsService = alertsService;
        this.farmService = farmService;
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * Returns the dashboard from the latest materialized snapshot. When no snapshot exists yet
     * (new tenant or scheduler not run), the snapshot is computed lazily and persisted.
     */
    @Transactional
    public ReportingDtos.DashboardResponse dashboard() {
        UUID tenantId = TenantContext.requireTenantId();
        return snapshotRepository.findFirstByTenantIdOrderByRefreshedAtDesc(tenantId)
                .map(this::toResponse)
                .orElseGet(this::refresh);
    }

    @Transactional
    public ReportingDtos.DashboardResponse refresh() {
        UUID tenantId = TenantContext.requireTenantId();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        long herdSize = indicatorService.herdSize().longValue();
        long openAlerts = alertsService.openAlertCount();
        BigDecimal births = indicatorService.birthsPeriod(monthStart, today);
        BigDecimal averageDailyGain = indicatorService.averageDailyGain();
        BigDecimal grossMargin = indicatorService.netMargin(monthStart, today);
        List<AlertsDtos.AlertResponse> criticalAlerts =
                alertsService.list("OPEN", "CRITICAL", null);

        DashboardSnapshotEntity snapshot = snapshotRepository
                .findFirstByTenantIdOrderByRefreshedAtDesc(tenantId)
                .orElseGet(DashboardSnapshotEntity::new);
        snapshot.id = snapshot.id == null ? UUID.randomUUID() : snapshot.id;
        snapshot.tenantId = tenantId;
        snapshot.farmId = defaultFarmId();
        snapshot.herdSize = herdSize;
        snapshot.openAlerts = openAlerts;
        snapshot.birthsThisMonth = births.longValue();
        snapshot.averageDailyGain = averageDailyGain;
        snapshot.grossMargin = grossMargin;
        snapshot.refreshedAt = Instant.now();
        snapshotRepository.save(snapshot);

        return new ReportingDtos.DashboardResponse(herdSize, openAlerts, births.longValue(),
                averageDailyGain, grossMargin, criticalAlerts);
    }

    private ReportingDtos.DashboardResponse toResponse(DashboardSnapshotEntity snapshot) {
        List<AlertsDtos.AlertResponse> criticalAlerts =
                alertsService.list("OPEN", "CRITICAL", null);
        return new ReportingDtos.DashboardResponse(snapshot.herdSize, snapshot.openAlerts,
                snapshot.birthsThisMonth, snapshot.averageDailyGain, snapshot.grossMargin,
                criticalAlerts);
    }

    private UUID defaultFarmId() {
        List<IdentityDtos.FarmResponse> farms = farmService.list();
        return farms.isEmpty() ? null : farms.getFirst().id();
    }
}
