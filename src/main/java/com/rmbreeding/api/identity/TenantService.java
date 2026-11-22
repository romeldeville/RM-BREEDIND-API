package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final QuotaService quotaService;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final RetentionProperties retentionProperties;

    public TenantService(TenantRepository tenantRepository, MembershipRepository membershipRepository,
            UserRepository userRepository, QuotaService quotaService, AuditService auditService,
            JdbcTemplate jdbcTemplate, RetentionProperties retentionProperties) {
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.quotaService = quotaService;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
        this.retentionProperties = retentionProperties;
    }

    public List<IdentityDtos.TenantResponse> listForUser(UUID userId) {
        List<MembershipEntity> memberships = membershipRepository.findByUserId(userId);
        return memberships.stream()
                .map(membership -> tenantRepository.findById(membership.tenantId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public IdentityDtos.TenantResponse createForUser(UUID userId, IdentityDtos.CreateTenantRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        TenantEntity tenant = provision(user, request.name(), request.slug(), request.country(),
                request.currency());
        auditService.record("TENANT_CREATED", "tenant", tenant.id);
        return toResponse(tenant);
    }

    @Transactional
    public TenantEntity provision(UserEntity user, String name, String slug, String country, String currency) {
        if (tenantRepository.existsBySlug(slug)) {
            throw new ConflictException("Tenant slug already used");
        }
        Instant now = Instant.now();
        TenantEntity tenant = new TenantEntity();
        tenant.id = UUID.randomUUID();
        tenant.name = name;
        tenant.slug = slug;
        tenant.status = "ACTIVE";
        tenant.planCode = "FREE";
        tenant.trialEndsAt = now.plus(14, ChronoUnit.DAYS);
        tenant.country = country;
        tenant.currency = currency;
        tenant.createdAt = now;
        tenant.updatedAt = now;
        tenantRepository.saveAndFlush(tenant);

        jdbcTemplate.update("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                INSERT INTO farms (id, tenant_id, name, code, country, currency, timezone)
                SELECT ?, ?, ?, 'MAIN', ?, ?, 'UTC' FROM ctx
                """, tenant.id.toString(), UUID.randomUUID(), tenant.id, name, country, currency);

        jdbcTemplate.update("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                INSERT INTO memberships (id, tenant_id, user_id, role, farm_scope)
                SELECT ?, ?, ?, 'OWNER', '{}' FROM ctx
                """, tenant.id.toString(), UUID.randomUUID(), tenant.id, user.id);

        quotaService.provision(tenant.id, "FREE");
        quotaService.setUsed(tenant.id, "farms", 1);
        quotaService.setUsed(tenant.id, "members", 1);
        return tenant;
    }

    public TenantEntity requireTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant not found"));
    }

    public void requireMembership(UUID tenantId, UUID userId) {
        if (!membershipRepository.existsByTenantIdAndUserId(tenantId, userId)) {
            throw new com.rmbreeding.api.shared.BusinessRuleException(
                    com.rmbreeding.api.shared.ErrorCode.FORBIDDEN, "User is not a member of this tenant");
        }
    }

    @Transactional
    public IdentityDtos.TenantResponse requestDeletion(UUID tenantId) {
        TenantEntity tenant = requireTenant(tenantId);
        if ("PENDING_DELETION".equals(tenant.status)) {
            return toResponse(tenant);
        }
        Instant now = Instant.now();
        tenant.status = "PENDING_DELETION";
        tenant.deleteRequestedAt = now;
        tenant.deleteAfter = now.plus(retentionProperties.getTenantDeletionGrace());
        tenant.updatedAt = now;
        tenantRepository.save(tenant);
        auditService.record("TENANT_DELETION_REQUESTED", "tenant", tenantId);
        return toResponse(tenant);
    }

    @Transactional
    public IdentityDtos.TenantResponse restoreDeletion(UUID tenantId) {
        TenantEntity tenant = requireTenant(tenantId);
        if (!"PENDING_DELETION".equals(tenant.status)) {
            return toResponse(tenant);
        }
        tenant.status = "ACTIVE";
        tenant.deleteRequestedAt = null;
        tenant.deleteAfter = null;
        tenant.updatedAt = Instant.now();
        tenantRepository.save(tenant);
        auditService.record("TENANT_DELETION_RESTORED", "tenant", tenantId);
        return toResponse(tenant);
    }

    @Transactional
    public Map<String, Object> exportTenant(UUID tenantId) {
        TenantEntity tenant = requireTenant(tenantId);
        List<Map<String, Object>> members = membershipRepository.findByTenantIdOrderByCreatedAtAsc(tenantId)
                .stream().map(m -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("userId", m.userId);
                    row.put("role", m.role);
                    row.put("status", m.status);
                    row.put("farmScope", m.farmScope);
                    row.put("createdAt", m.createdAt);
                    return row;
                }).toList();
        List<Map<String, Object>> farms = jdbcTemplate.queryForList(
                "SELECT id, name, code, country, currency, timezone, address, archived_at, created_at "
                        + "FROM farms WHERE tenant_id = ?",
                tenantId);
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("tenant", toResponse(tenant));
        export.put("members", members);
        export.put("farms", farms);
        export.put("exportedAt", Instant.now());
        export.put("formatVersion", "1.0");
        auditService.record("TENANT_EXPORTED", "tenant", tenantId);
        return export;
    }

    public IdentityDtos.TenantResponse toResponse(TenantEntity tenant) {
        return new IdentityDtos.TenantResponse(tenant.id, tenant.name, tenant.slug, tenant.status,
                tenant.planCode, tenant.trialEndsAt, tenant.deleteRequestedAt, tenant.deleteAfter);
    }
}
