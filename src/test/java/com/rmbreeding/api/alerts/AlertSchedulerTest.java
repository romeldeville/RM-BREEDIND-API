package com.rmbreeding.api.alerts;

import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertSchedulerTest {

    @Mock
    private AlertRepository alertRepository;

    @Test
    void runsGeneratorsForEachTenant() {
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        when(alertRepository.findAllTenantIds()).thenReturn(List.of(t1, t2));
        AlertGenerator generator = mock(AlertGenerator.class);

        new AlertScheduler(List.of(generator), alertRepository).runGeneration();

        verify(generator, times(2)).generate(any(LocalDate.class));
        assertThat(TenantContext.getTenantId()).isNull();
    }
}
