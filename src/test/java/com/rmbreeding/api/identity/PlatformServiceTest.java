package com.rmbreeding.api.identity;

import com.rmbreeding.api.identity.PlatformDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AuditService auditService;

    private PlatformService platformService;

    @BeforeEach
    void setUp() {
        platformService = new PlatformService(jdbcTemplate, auditService);
    }

    @Test
    void listTenantsExecutesFilteredQuery() {
        UUID id = UUID.randomUUID();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("%ferme%"), eq("%ferme%")))
                .thenReturn(List.of(new PlatformTenantDto(id, "Ferme A", "ferme-a", "ACTIVE", "PRO", 2, 50, Instant.now())));

        List<PlatformTenantDto> list = platformService.listTenants("ferme");
        assertThat(list).hasSize(1);
        assertThat(list.getFirst().name()).isEqualTo("Ferme A");
    }

    @Test
    void suspendAndReactivateTenantUpdatesStatusAndAudits() {
        UUID id = UUID.randomUUID();

        platformService.suspendTenant(id);
        verify(jdbcTemplate).update(contains("SUSPENDED"), eq(id));
        verify(auditService).record(eq("PLATFORM_SUSPEND_TENANT"), eq("TENANT"), eq(id));

        platformService.reactivateTenant(id);
        verify(jdbcTemplate).update(contains("ACTIVE"), eq(id));
        verify(auditService).record(eq("PLATFORM_REACTIVATE_TENANT"), eq("TENANT"), eq(id));
    }

    @Test
    void getMetricsCalculatesAggregates() {
        when(jdbcTemplate.queryForObject(eq("SELECT count(*) FROM tenants"), eq(Long.class))).thenReturn(10L);
        when(jdbcTemplate.queryForObject(eq("SELECT count(*) FROM tenants WHERE status = 'ACTIVE'"), eq(Long.class))).thenReturn(8L);
        when(jdbcTemplate.queryForObject(eq("SELECT count(*) FROM farms"), eq(Long.class))).thenReturn(15L);
        when(jdbcTemplate.queryForObject(eq("SELECT count(*) FROM animals"), eq(Long.class))).thenReturn(1200L);
        when(jdbcTemplate.queryForObject(eq("SELECT count(*) FROM users"), eq(Long.class))).thenReturn(25L);
        when(jdbcTemplate.queryForObject(contains("sum(p.price_cents)"), eq(Long.class))).thenReturn(20000000L);

        PlatformMetricsDto metrics = platformService.getMetrics();
        assertThat(metrics.totalTenants()).isEqualTo(10L);
        assertThat(metrics.activeTenants()).isEqualTo(8L);
        assertThat(metrics.totalAnimals()).isEqualTo(1200L);
        assertThat(metrics.monthlyRecurringRevenueCents()).isEqualTo(20000000L);
    }

    @Test
    void exportTenantsCsvProducesValidHeadersAndRows() {
        UUID id = UUID.randomUUID();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("%"), eq("%")))
                .thenReturn(List.of(new PlatformTenantDto(id, "Ferme Démo", "ferme-demo", "ACTIVE", "PRO", 1, 100, Instant.now())));

        String csv = platformService.exportTenantsCsv();
        assertThat(csv).startsWith("id,name,slug,status,planCode,farmsCount,animalsCount,createdAt\n");
        assertThat(csv).contains("Ferme Démo");
    }
}
