package com.rmbreeding.api.reporting;

import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingSchedulerTest {

    @Mock
    private DashboardService dashboardService;
    @Mock
    private DashboardSnapshotRepository snapshotRepository;

    @Test
    void refreshesSnapshotsForEachTenant() {
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        when(snapshotRepository.findAllTenantIds()).thenReturn(List.of(t1, t2));

        new ReportingScheduler(dashboardService, snapshotRepository).refreshSnapshots();

        verify(dashboardService, times(2)).refresh();
        assertThat(TenantContext.getTenantId()).isNull();
    }
}
