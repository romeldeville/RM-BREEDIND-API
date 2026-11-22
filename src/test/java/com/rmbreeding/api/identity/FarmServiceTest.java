package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmServiceTest {

    @Mock
    private FarmRepository farmRepository;
    @Mock
    private QuotaService quotaService;
    @Mock
    private AuditService auditService;

    private FarmService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FarmService(farmRepository, quotaService, auditService);
        TenantContext.setTenantId(tenantId);
        AuthContext.set(new AuthPrincipal(userId, tenantId, List.of("OWNER"), false, null));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        AuthContext.clear();
    }

    @Test
    void listMapsFarms() {
        when(farmRepository.findByTenantIdOrderByName(tenantId)).thenReturn(List.of(farm()));

        List<IdentityDtos.FarmResponse> farms = service.list();

        assertThat(farms).hasSize(1);
        assertThat(farms.getFirst().name()).isEqualTo("Ferme");
    }

    @Test
    void createAppliesDefaultsAndUpdatesQuota() {
        when(farmRepository.countByTenantId(tenantId)).thenReturn(1L);
        when(farmRepository.existsByTenantIdAndCode(tenantId, "F2")).thenReturn(false);
        when(farmRepository.save(any(FarmEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        IdentityDtos.FarmResponse response = service.create(
                new IdentityDtos.CreateFarmRequest("Ferme 2", "F2", "CI", null, null));

        assertThat(response.code()).isEqualTo("F2");
        assertThat(response.currency()).isEqualTo("XOF");
        assertThat(response.timezone()).isEqualTo("UTC");
        verify(quotaService).requireCapacity(tenantId, "farms", 1L);
        verify(quotaService).setUsed(tenantId, "farms", 2);
        verify(auditService).record("FARM_CREATED", "farm", response.id());
    }

    @Test
    void createRejectsDuplicateCode() {
        when(farmRepository.countByTenantId(tenantId)).thenReturn(1L);
        when(farmRepository.existsByTenantIdAndCode(tenantId, "F1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new IdentityDtos.CreateFarmRequest("Ferme", "F1", "CI", "XOF", "UTC")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("code already used");

        verify(farmRepository, never()).save(any());
    }

    @Test
    void getRejectsUnknownFarm() {
        UUID farmId = UUID.randomUUID();
        when(farmRepository.findByIdAndTenantId(farmId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(farmId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void patchUpdatesProvidedFieldsAndAudits() {
        UUID farmId = UUID.randomUUID();
        FarmEntity farm = farm();
        farm.id = farmId;
        when(farmRepository.findByIdAndTenantId(farmId, tenantId)).thenReturn(Optional.of(farm));
        when(farmRepository.save(any(FarmEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        IdentityDtos.FarmResponse response = service.patch(farmId,
                new IdentityDtos.FarmPatchRequest("Nouveau nom", "SN", "XOF", "Africa/Dakar"));

        assertThat(response.name()).isEqualTo("Nouveau nom");
        assertThat(response.country()).isEqualTo("SN");
        assertThat(response.timezone()).isEqualTo("Africa/Dakar");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
        verify(auditService).recordChange(eq("FARM_UPDATED"), eq("farm"), eq(farmId),
                before.capture(), after.capture());
        assertThat(before.getValue()).containsEntry("name", "Ferme");
        assertThat(after.getValue()).containsEntry("name", "Nouveau nom");
    }

    private FarmEntity farm() {
        FarmEntity farm = new FarmEntity();
        farm.id = UUID.randomUUID();
        farm.tenantId = tenantId;
        farm.name = "Ferme";
        farm.code = "F1";
        farm.country = "CI";
        farm.currency = "XOF";
        farm.timezone = "UTC";
        return farm;
    }
}
