package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
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
class MemberServiceTest {

    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FarmRepository farmRepository;
    @Mock
    private AuditService auditService;

    private MemberService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MemberService(membershipRepository, userRepository, farmRepository, auditService);
        TenantContext.setTenantId(tenantId);
        AuthContext.set(new AuthPrincipal(userId, tenantId, List.of("OWNER"), false, null));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        AuthContext.clear();
    }

    @Test
    void listByTenantReturnsMembersWithUserDetails() {
        MembershipEntity membership = membership("OWNER", List.of());
        UserEntity user = user("owner@rmbreeding.local", "Owner");
        when(membershipRepository.findByTenantIdOrderByCreatedAtAsc(tenantId)).thenReturn(List.of(membership));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        List<IdentityDtos.MemberResponse> members = service.listByTenant(tenantId);

        assertThat(members).hasSize(1);
        assertThat(members.getFirst().email()).isEqualTo("owner@rmbreeding.local");
        assertThat(members.getFirst().role()).isEqualTo("OWNER");
    }

    @Test
    void listByFarmFiltersMembersOutsideFarmScope() {
        UUID farmId = UUID.randomUUID();
        UUID otherFarm = UUID.randomUUID();
        FarmEntity farm = farm(farmId);
        MembershipEntity allFarms = membership("OWNER", List.of());
        MembershipEntity inScope = membership("WORKER", List.of(farmId));
        MembershipEntity outOfScope = membership("VET", List.of(otherFarm));
        when(farmRepository.findByIdAndTenantId(farmId, tenantId)).thenReturn(Optional.of(farm));
        when(membershipRepository.findByTenantIdOrderByCreatedAtAsc(tenantId))
                .thenReturn(List.of(allFarms, inScope, outOfScope));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        List<IdentityDtos.MemberResponse> members = service.listByFarm(farmId);

        assertThat(members).hasSize(2);
        assertThat(members).extracting(IdentityDtos.MemberResponse::role)
                .containsExactly("OWNER", "WORKER");
    }

    @Test
    void listByFarmRejectsUnknownFarm() {
        UUID farmId = UUID.randomUUID();
        when(farmRepository.findByIdAndTenantId(farmId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listByFarm(farmId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Farm not found");
    }

    @Test
    void updateChangesRoleAndScopeAndAudits() {
        UUID farmId = UUID.randomUUID();
        MembershipEntity membership = membership("WORKER", List.of());
        when(membershipRepository.findById(membership.id)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any(MembershipEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user("worker@rmbreeding.local", "Worker")));

        IdentityDtos.MemberResponse response = service.update(tenantId, membership.id,
                new IdentityDtos.UpdateMemberRequest("MANAGER", List.of(farmId)));

        assertThat(response.role()).isEqualTo("MANAGER");
        assertThat(membership.farmScope).containsExactly(farmId);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
        verify(auditService).recordChange(eq("MEMBER_UPDATED"), eq("membership"), eq(membership.id),
                before.capture(), after.capture());
        assertThat(before.getValue()).containsEntry("role", "WORKER");
        assertThat(after.getValue()).containsEntry("role", "MANAGER");
    }

    @Test
    void updateRejectsDemotingLastOwner() {
        MembershipEntity owner = membership("OWNER", List.of());
        when(membershipRepository.findById(owner.id)).thenReturn(Optional.of(owner));
        when(membershipRepository.findByTenantIdOrderByCreatedAtAsc(tenantId)).thenReturn(List.of(owner));

        assertThatThrownBy(() -> service.update(tenantId, owner.id,
                new IdentityDtos.UpdateMemberRequest("MANAGER", null)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.CONFLICT));

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void updateAllowsDemotingOwnerWhenAnotherRemains() {
        MembershipEntity first = membership("OWNER", List.of());
        MembershipEntity second = membership("OWNER", List.of());
        when(membershipRepository.findById(first.id)).thenReturn(Optional.of(first));
        when(membershipRepository.findByTenantIdOrderByCreatedAtAsc(tenantId))
                .thenReturn(List.of(first, second));
        when(membershipRepository.save(any(MembershipEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        IdentityDtos.MemberResponse response = service.update(tenantId, first.id,
                new IdentityDtos.UpdateMemberRequest("ACCOUNTANT", null));

        assertThat(response.role()).isEqualTo("ACCOUNTANT");
    }

    @Test
    void updateRejectsMembershipFromAnotherTenant() {
        MembershipEntity foreign = membership("OWNER", List.of());
        foreign.tenantId = UUID.randomUUID();
        when(membershipRepository.findById(foreign.id)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.update(tenantId, foreign.id,
                new IdentityDtos.UpdateMemberRequest("MANAGER", null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void removeRejectsLastOwner() {
        MembershipEntity owner = membership("OWNER", List.of());
        when(membershipRepository.findById(owner.id)).thenReturn(Optional.of(owner));
        when(membershipRepository.findByTenantIdOrderByCreatedAtAsc(tenantId)).thenReturn(List.of(owner));

        assertThatThrownBy(() -> service.remove(tenantId, owner.id))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("last owner");
        verify(membershipRepository, never()).delete(any());
    }

    @Test
    void removeDeletesNonOwnerAndAudits() {
        MembershipEntity worker = membership("WORKER", List.of());
        when(membershipRepository.findById(worker.id)).thenReturn(Optional.of(worker));

        service.remove(tenantId, worker.id);

        verify(membershipRepository).delete(worker);
        verify(auditService).record("MEMBER_REMOVED", "membership", worker.id);
    }

    @Test
    void tenantAccessRequiresAuthentication() {
        AuthContext.clear();

        assertThatThrownBy(() -> FarmService.requireTenantAccess(tenantId))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void tenantAccessRejectsAnotherTenantAndAllowsPlatformAdmin() {
        AuthContext.set(new AuthPrincipal(userId, UUID.randomUUID(), List.of("OWNER"), false, null));
        assertThatThrownBy(() -> FarmService.requireTenantAccess(tenantId))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));

        AuthContext.set(new AuthPrincipal(userId, null, List.of("PLATFORM_ADMIN"), true, null));
        FarmService.requireTenantAccess(tenantId);
    }

    private MembershipEntity membership(String role, List<UUID> scope) {
        MembershipEntity membership = new MembershipEntity();
        membership.id = UUID.randomUUID();
        membership.tenantId = tenantId;
        membership.userId = userId;
        membership.role = role;
        membership.status = "ACTIVE";
        membership.farmScope = new ArrayList<>(scope);
        membership.createdAt = Instant.now();
        return membership;
    }

    private static UserEntity user(String email, String fullName) {
        UserEntity user = new UserEntity();
        user.id = UUID.randomUUID();
        user.email = email;
        user.fullName = fullName;
        user.status = "ACTIVE";
        return user;
    }

    private FarmEntity farm(UUID farmId) {
        FarmEntity farm = new FarmEntity();
        farm.id = farmId;
        farm.tenantId = tenantId;
        farm.name = "Ferme";
        farm.code = "F1";
        farm.country = "CI";
        farm.currency = "XOF";
        farm.timezone = "UTC";
        return farm;
    }
}
