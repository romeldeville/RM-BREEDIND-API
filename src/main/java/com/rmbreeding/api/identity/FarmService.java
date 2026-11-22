package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ConflictException;
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
public class FarmService {

    private final FarmRepository farmRepository;
    private final QuotaService quotaService;
    private final AuditService auditService;

    public FarmService(FarmRepository farmRepository, QuotaService quotaService, AuditService auditService) {
        this.farmRepository = farmRepository;
        this.quotaService = quotaService;
        this.auditService = auditService;
    }

    public List<IdentityDtos.FarmResponse> list() {
        UUID tenantId = TenantContext.requireTenantId();
        return farmRepository.findByTenantIdOrderByName(tenantId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public IdentityDtos.FarmResponse create(IdentityDtos.CreateFarmRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        long current = farmRepository.countByTenantId(tenantId);
        quotaService.requireCapacity(tenantId, "farms", current);
        if (farmRepository.existsByTenantIdAndCode(tenantId, request.code())) {
            throw new ConflictException("Farm code already used in this tenant");
        }
        Instant now = Instant.now();
        FarmEntity farm = new FarmEntity();
        farm.id = UUID.randomUUID();
        farm.tenantId = tenantId;
        farm.name = request.name();
        farm.code = request.code();
        farm.country = request.country();
        farm.currency = request.currency() == null ? "XOF" : request.currency();
        farm.timezone = request.timezone() == null ? "UTC" : request.timezone();
        farm.createdAt = now;
        farm.updatedAt = now;
        farmRepository.save(farm);
        quotaService.setUsed(tenantId, "farms", (int) current + 1);
        auditService.record("FARM_CREATED", "farm", farm.id);
        return toResponse(farm);
    }

    public IdentityDtos.FarmResponse get(UUID farmId) {
        UUID tenantId = TenantContext.requireTenantId();
        return toResponse(requireFarm(farmId, tenantId));
    }

    @Transactional
    public IdentityDtos.FarmResponse patch(UUID farmId, IdentityDtos.FarmPatchRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        FarmEntity farm = requireFarm(farmId, tenantId);
        Map<String, Object> before = farmSnapshot(farm);
        if (request.name() != null) {
            farm.name = request.name();
        }
        if (request.country() != null) {
            farm.country = request.country();
        }
        if (request.currency() != null) {
            farm.currency = request.currency();
        }
        if (request.timezone() != null) {
            farm.timezone = request.timezone();
        }
        farm.updatedAt = Instant.now();
        farmRepository.save(farm);
        auditService.recordChange("FARM_UPDATED", "farm", farm.id, before, farmSnapshot(farm));
        return toResponse(farm);
    }

    public FarmEntity requireFarm(UUID farmId, UUID tenantId) {
        return farmRepository.findByIdAndTenantId(farmId, tenantId)
                .orElseThrow(() -> new NotFoundException("Farm not found"));
    }

    private IdentityDtos.FarmResponse toResponse(FarmEntity farm) {
        return new IdentityDtos.FarmResponse(farm.id, farm.tenantId, farm.name, farm.code, farm.country,
                farm.currency, farm.timezone);
    }

    private static Map<String, Object> farmSnapshot(FarmEntity farm) {
        return Map.of(
                "name", farm.name == null ? "" : farm.name,
                "country", farm.country == null ? "" : farm.country,
                "currency", farm.currency == null ? "" : farm.currency,
                "timezone", farm.timezone == null ? "" : farm.timezone);
    }

    public static void requireTenantAccess(UUID tenantId) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null) {
            throw new BusinessRuleException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        if (principal.platformAdmin()) {
            return;
        }
        if (principal.tenantId() == null || !principal.tenantId().equals(tenantId)) {
            throw new BusinessRuleException(ErrorCode.FORBIDDEN, "Tenant access denied");
        }
    }
}
