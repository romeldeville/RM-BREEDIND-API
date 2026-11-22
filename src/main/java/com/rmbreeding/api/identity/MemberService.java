package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MemberService {

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final FarmRepository farmRepository;
    private final AuditService auditService;

    public MemberService(MembershipRepository membershipRepository, UserRepository userRepository,
            FarmRepository farmRepository, AuditService auditService) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.farmRepository = farmRepository;
        this.auditService = auditService;
    }

    public List<IdentityDtos.MemberResponse> listByTenant(UUID tenantId) {
        FarmService.requireTenantAccess(tenantId);
        return membershipRepository.findByTenantIdOrderByCreatedAtAsc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<IdentityDtos.MemberResponse> listByFarm(UUID farmId) {
        UUID tenantId = TenantContext.requireTenantId();
        FarmEntity farm = farmRepository.findByIdAndTenantId(farmId, tenantId)
                .orElseThrow(() -> new NotFoundException("Farm not found"));
        return membershipRepository.findByTenantIdOrderByCreatedAtAsc(farm.tenantId).stream()
                .filter(membership -> membership.farmScope.isEmpty()
                        || membership.farmScope.contains(farmId))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public IdentityDtos.MemberResponse update(UUID tenantId, UUID membershipId,
            IdentityDtos.UpdateMemberRequest request) {
        FarmService.requireTenantAccess(tenantId);
        MembershipEntity membership = membershipRepository.findById(membershipId)
                .filter(item -> tenantId.equals(item.tenantId))
                .orElseThrow(() -> new NotFoundException("Membership not found"));
        if ("OWNER".equals(membership.role) && request.role() != null && !"OWNER".equals(request.role())
                && ownerCount(tenantId) <= 1) {
            throw new BusinessRuleException(ErrorCode.CONFLICT, "Cannot demote the last owner");
        }
        Map<String, Object> before = memberSnapshot(membership);
        if (request.role() != null) {
            membership.role = request.role();
        }
        if (request.farmScope() != null) {
            membership.farmScope = request.farmScope();
        }
        membership.updatedAt = Instant.now();
        membershipRepository.save(membership);
        auditService.recordChange("MEMBER_UPDATED", "membership", membership.id, before,
                memberSnapshot(membership));
        return toResponse(membership);
    }

    @Transactional
    public void remove(UUID tenantId, UUID membershipId) {
        FarmService.requireTenantAccess(tenantId);
        MembershipEntity membership = membershipRepository.findById(membershipId)
                .filter(item -> tenantId.equals(item.tenantId))
                .orElseThrow(() -> new NotFoundException("Membership not found"));
        if ("OWNER".equals(membership.role) && ownerCount(tenantId) <= 1) {
            throw new BusinessRuleException(ErrorCode.CONFLICT, "Cannot remove the last owner");
        }
        membershipRepository.delete(membership);
        auditService.record("MEMBER_REMOVED", "membership", membership.id);
    }

    private long ownerCount(UUID tenantId) {
        return membershipRepository.findByTenantIdOrderByCreatedAtAsc(tenantId).stream()
                .filter(membership -> "OWNER".equals(membership.role))
                .count();
    }

    private static Map<String, Object> memberSnapshot(MembershipEntity membership) {
        return Map.of(
                "role", membership.role == null ? "" : membership.role,
                "farmScope", membership.farmScope == null ? List.of() : List.copyOf(membership.farmScope),
                "status", membership.status == null ? "" : membership.status);
    }

    private IdentityDtos.MemberResponse toResponse(MembershipEntity membership) {
        UserEntity user = userRepository.findById(membership.userId).orElse(null);
        return new IdentityDtos.MemberResponse(membership.id, membership.userId,
                user == null ? null : user.email,
                user == null ? null : user.fullName,
                membership.role, List.copyOf(membership.farmScope), membership.status);
    }
}
