package com.rmbreeding.api.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private AuditService auditService;

    private RetentionService service;

    @BeforeEach
    void setUp() {
        RetentionProperties properties = new RetentionProperties();
        properties.setTokenCleanupAfter(Duration.ofDays(7));
        properties.setTenantDeletionGrace(Duration.ofDays(30));
        service = new RetentionService(jdbcTemplate, tenantRepository, auditService, properties);
    }

    @Test
    void purgeExpiredTokensDeletesRevokedAndExpiredArtifacts() {
        when(jdbcTemplate.update(anyString(), any(Timestamp.class))).thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(Timestamp.class), any(Timestamp.class)))
                .thenReturn(2, 3, 4);

        int purged = service.purgeExpiredTokens();

        assertThat(purged).isEqualTo(10);
    }

    @Test
    void purgeDeletedTenantsAnonymizesAuditAndDeletesTenant() {
        UUID tenantId = UUID.randomUUID();
        TenantEntity tenant = new TenantEntity();
        tenant.id = tenantId;
        tenant.status = "PENDING_DELETION";
        tenant.deleteAfter = Instant.now().minusSeconds(60);

        when(tenantRepository.findByStatusAndDeleteAfterBefore(eq("PENDING_DELETION"), any(Instant.class)))
                .thenReturn(List.of(tenant));
        when(jdbcTemplate.update(anyString(), eq(tenantId))).thenReturn(1);

        int purged = service.purgeDeletedTenants();

        assertThat(purged).isEqualTo(1);
        verify(jdbcTemplate).update("UPDATE audit_log SET actor_id = NULL WHERE tenant_id = ?", tenantId);
        verify(jdbcTemplate).update("DELETE FROM memberships WHERE tenant_id = ?", tenantId);
        verify(jdbcTemplate).update("DELETE FROM invitations WHERE tenant_id = ?", tenantId);
        verify(tenantRepository).delete(tenant);
        verify(auditService).record("TENANT_PURGED", "tenant", tenantId);
    }
}
