package com.rmbreeding.api.alerts;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// P11-08 / P11-09 Scheduled alert generation
@Component
class AlertScheduler {

    private final List<AlertGenerator> generators;
    private final AlertRepository alertRepository;

    AlertScheduler(List<AlertGenerator> generators, AlertRepository alertRepository) {
        this.generators = generators;
        this.alertRepository = alertRepository;
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 */15 * * * *")
    public void generateDueAlerts() {
        runGeneration();
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 5 * * *")
    public void prepareToday() {
        runGeneration();
    }

    public void runGeneration() {
        LocalDate today = LocalDate.now();
        for (UUID tenantId : alertRepository.findAllTenantIds()) {
            try {
                com.rmbreeding.api.shared.TenantContext.setTenantId(tenantId);
                for (AlertGenerator generator : generators) {
                    generator.generate(today);
                }
            } finally {
                com.rmbreeding.api.shared.TenantContext.clear();
            }
        }
    }
}
