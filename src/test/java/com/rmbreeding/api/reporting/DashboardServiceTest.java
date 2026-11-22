package com.rmbreeding.api.reporting;

import com.rmbreeding.api.alerts.AlertsDtos;
import com.rmbreeding.api.alerts.AlertsService;
import com.rmbreeding.api.identity.FarmService;
import com.rmbreeding.api.identity.IdentityDtos;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private IndicatorService indicatorService;
    @Mock
    private AlertsService alertsService;
    @Mock
    private FarmService farmService;
    @Mock
    private DashboardSnapshotRepository snapshotRepository;

    private DashboardService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DashboardService(indicatorService, alertsService, farmService, snapshotRepository);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void computesAndPersistsDashboard() {
        UUID farmId = UUID.randomUUID();
        AlertsDtos.AlertResponse alert = new AlertsDtos.AlertResponse(UUID.randomUUID(), farmId,
                "FARROWING_DUE", "CRITICAL", "Mise bas", "message", LocalDate.now(), "OPEN", "cycle",
                UUID.randomUUID(), null, null, Instant.now(), null);
        when(indicatorService.herdSize()).thenReturn(new BigDecimal("42"));
        when(indicatorService.birthsPeriod(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("5"));
        when(indicatorService.averageDailyGain()).thenReturn(new BigDecimal("0.80"));
        when(indicatorService.netMargin(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("3000"));
        when(alertsService.openAlertCount()).thenReturn(7L);
        when(alertsService.list("OPEN", "CRITICAL", null)).thenReturn(List.of(alert));
        when(farmService.list()).thenReturn(List.of(new IdentityDtos.FarmResponse(farmId, tenantId,
                "Ferme", "F1", null, "XOF", null)));
        when(snapshotRepository.findFirstByTenantIdOrderByRefreshedAtDesc(tenantId))
                .thenReturn(Optional.empty());

        ReportingDtos.DashboardResponse response = service.dashboard();

        assertThat(response.herdSize()).isEqualTo(42);
        assertThat(response.openAlerts()).isEqualTo(7);
        assertThat(response.birthsThisMonth()).isEqualTo(5);
        assertThat(response.averageDailyGain()).isEqualByComparingTo("0.80");
        assertThat(response.grossMargin()).isEqualByComparingTo("3000");
        assertThat(response.criticalAlerts()).hasSize(1);
        verify(snapshotRepository).save(any(DashboardSnapshotEntity.class));
    }

    @Test
    void reusesExistingSnapshot() {
        DashboardSnapshotEntity existing = new DashboardSnapshotEntity();
        existing.id = UUID.randomUUID();
        existing.tenantId = tenantId;
        existing.refreshedAt = Instant.now();
        when(indicatorService.herdSize()).thenReturn(new BigDecimal("10"));
        when(indicatorService.birthsPeriod(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("1"));
        when(indicatorService.averageDailyGain()).thenReturn(null);
        when(indicatorService.netMargin(any(LocalDate.class), any(LocalDate.class))).thenReturn(null);
        when(alertsService.openAlertCount()).thenReturn(0L);
        when(alertsService.list("OPEN", "CRITICAL", null)).thenReturn(List.of());
        when(farmService.list()).thenReturn(List.of());
        when(snapshotRepository.findFirstByTenantIdOrderByRefreshedAtDesc(tenantId))
                .thenReturn(Optional.of(existing));

        ReportingDtos.DashboardResponse response = service.refresh();

        assertThat(response.herdSize()).isEqualTo(10);
        assertThat(response.averageDailyGain()).isNull();
        assertThat(existing.herdSize).isEqualTo(10);
    }

    @Test
    void servesLatestSnapshotWithoutRecomputing() {
        DashboardSnapshotEntity snapshot = new DashboardSnapshotEntity();
        snapshot.id = UUID.randomUUID();
        snapshot.tenantId = tenantId;
        snapshot.herdSize = 12;
        snapshot.openAlerts = 3;
        snapshot.birthsThisMonth = 2;
        snapshot.averageDailyGain = new BigDecimal("0.75");
        snapshot.grossMargin = new BigDecimal("1200");
        snapshot.refreshedAt = Instant.now();
        AlertsDtos.AlertResponse alert = new AlertsDtos.AlertResponse(UUID.randomUUID(), null,
                "LOW_STOCK", "CRITICAL", "Stock bas", "message", LocalDate.now(), "OPEN", "stock",
                null, null, null, Instant.now(), null);
        when(snapshotRepository.findFirstByTenantIdOrderByRefreshedAtDesc(tenantId))
                .thenReturn(Optional.of(snapshot));
        when(alertsService.list("OPEN", "CRITICAL", null)).thenReturn(List.of(alert));

        ReportingDtos.DashboardResponse response = service.dashboard();

        assertThat(response.herdSize()).isEqualTo(12);
        assertThat(response.openAlerts()).isEqualTo(3);
        assertThat(response.birthsThisMonth()).isEqualTo(2);
        assertThat(response.averageDailyGain()).isEqualByComparingTo("0.75");
        assertThat(response.grossMargin()).isEqualByComparingTo("1200");
        assertThat(response.criticalAlerts()).hasSize(1);
        verifyNoInteractions(indicatorService, farmService);
        verify(snapshotRepository, never()).save(any(DashboardSnapshotEntity.class));
    }
}
