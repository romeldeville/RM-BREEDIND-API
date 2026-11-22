package com.rmbreeding.api.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantLifecycleServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private QuotaService quotaService;
    @Mock
    private AuditService auditService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private TenantService service;
    private RetentionProperties retentionProperties;

    @BeforeEach
    void setUp() {
        retentionProperties = new RetentionProperties();
        retentionProperties.setTenantDeletionGrace(Duration.ofDays(30));
        service = new TenantService(tenantRepository, membershipRepository, userRepository,
                quotaService, auditService, jdbcTemplate, retentionProperties);
    }

    @Test
    void requestDeletionMarksTenantPendingAndAudits() {
        UUID tenantId = UUID.randomUUID();
        TenantEntity tenant = tenant(tenantId, "ACTIVE");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(TenantEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        IdentityDtos.TenantResponse response = service.requestDeletion(tenantId);

        assertThat(response.status()).isEqualTo("PENDING_DELETION");
        assertThat(tenant.status).isEqualTo("PENDING_DELETION");
        assertThat(tenant.deleteRequestedAt).isNotNull();
        assertThat(tenant.deleteAfter).isAfter(tenant.deleteRequestedAt);
        assertThat(Duration.between(tenant.deleteRequestedAt, tenant.deleteAfter).toDays()).isEqualTo(30);
        verify(auditService).record("TENANT_DELETION_REQUESTED", "tenant", tenantId);
    }

    @Test
    void restoreDeletionReactivatesTenantAndAudits() {
        UUID tenantId = UUID.randomUUID();
        TenantEntity tenant = tenant(tenantId, "PENDING_DELETION");
        tenant.deleteRequestedAt = Instant.now().minus(Duration.ofDays(2));
        tenant.deleteAfter = Instant.now().plus(Duration.ofDays(28));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(TenantEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        IdentityDtos.TenantResponse response = service.restoreDeletion(tenantId);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(tenant.deleteRequestedAt).isNull();
        assertThat(tenant.deleteAfter).isNull();
        verify(auditService).record("TENANT_DELETION_RESTORED", "tenant", tenantId);
    }

    @Test
    void exportTenantReturnsMembersFarmsAndAudits() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantEntity tenant = tenant(tenantId, "ACTIVE");
        MembershipEntity membership = new MembershipEntity();
        membership.tenantId = tenantId;
        membership.userId = userId;
        membership.role = "OWNER";
        membership.status = "ACTIVE";
        membership.farmScope = java.util.List.of();
        membership.createdAt = Instant.parse("2026-11-01T10:00:00Z");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(membershipRepository.findByTenantIdOrderByCreatedAtAsc(tenantId)).thenReturn(List.of(membership));
        when(jdbcTemplate.queryForList(anyString(), eq(tenantId)))
                .thenReturn(List.of(Map.of("id", UUID.randomUUID(), "name", "Ferme")));

        Map<String, Object> export = service.exportTenant(tenantId);

        assertThat(export).containsKeys("tenant", "members", "farms", "exportedAt", "formatVersion");
        assertThat((List<?>) export.get("members")).hasSize(1);
        assertThat((List<?>) export.get("farms")).hasSize(1);
        verify(auditService).record("TENANT_EXPORTED", "tenant", tenantId);
    }

    private static TenantEntity tenant(UUID tenantId, String status) {
        TenantEntity tenant = new TenantEntity();
        tenant.id = tenantId;
        tenant.name = "Tenant";
        tenant.slug = "tenant";
        tenant.status = status;
        tenant.planCode = "FREE";
        tenant.createdAt = Instant.now();
        tenant.updatedAt = Instant.now();
        return tenant;
    }
}
