package com.rmbreeding.api.reporting;

import com.rmbreeding.api.shared.TenantContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ReportingScheduler {

    private final DashboardService dashboardService;
    private final DashboardSnapshotRepository snapshotRepository;

    public ReportingScheduler(DashboardService dashboardService,
            DashboardSnapshotRepository snapshotRepository) {
        this.dashboardService = dashboardService;
        this.snapshotRepository = snapshotRepository;
    }

    @Scheduled(cron = "0 */15 * * * *")
    public void refreshSnapshots() {
        for (UUID tenantId : snapshotRepository.findAllTenantIds()) {
            try {
                TenantContext.setTenantId(tenantId);
                dashboardService.refresh();
            } catch (RuntimeException ignored) {
                // keep refreshing other tenants
            } finally {
                TenantContext.clear();
            }
        }
    }
}
