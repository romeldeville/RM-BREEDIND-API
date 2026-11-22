package com.rmbreeding.api.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * P16-06 - politique de conservation : purge des jetons expirés et exécution des suppressions
 * de tenants arrivées à échéance.
 */
@Service
public class RetentionService {

    private static final Logger LOG = LoggerFactory.getLogger(RetentionService.class);

    private final JdbcTemplate jdbcTemplate;
    private final TenantRepository tenantRepository;
    private final AuditService auditService;
    private final RetentionProperties properties;

    public RetentionService(JdbcTemplate jdbcTemplate, TenantRepository tenantRepository,
            AuditService auditService, RetentionProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.retention.cron:0 30 3 * * *}")
    @Transactional
    public void run() {
        int tokens = purgeExpiredTokens();
        int tenants = purgeDeletedTenants();
        if (tokens > 0 || tenants > 0) {
            LOG.info("Retention run completed: purgedTokens={} purgedTenants={}", tokens, tenants);
        }
    }

    public int purgeExpiredTokens() {
        Instant cutoff = Instant.now().minus(properties.getTokenCleanupAfter());
        Timestamp ts = Timestamp.from(cutoff);
        int total = 0;
        total += jdbcTemplate.update(
                "DELETE FROM refresh_tokens WHERE revoked_at IS NOT NULL OR expires_at < ?", ts);
        total += jdbcTemplate.update("DELETE FROM otp_codes WHERE expires_at < ? OR created_at < ?", ts, ts);
        total += jdbcTemplate.update(
                "DELETE FROM email_verification_tokens WHERE consumed_at IS NOT NULL OR expires_at < ? OR created_at < ?",
                ts, ts);
        total += jdbcTemplate.update(
                "DELETE FROM password_reset_tokens WHERE consumed_at IS NOT NULL OR expires_at < ? OR created_at < ?",
                ts, ts);
        return total;
    }

    @Transactional
    public int purgeDeletedTenants() {
        Instant now = Instant.now();
        List<TenantEntity> due = tenantRepository.findByStatusAndDeleteAfterBefore("PENDING_DELETION", now);
        for (TenantEntity tenant : due) {
            hardDeleteTenant(tenant);
        }
        return due.size();
    }

    private void hardDeleteTenant(TenantEntity tenant) {
        UUID tenantId = tenant.id;
        // Audit trail kept append-only by policy; anonymize tenant context and remove tenant data.
        jdbcTemplate.update("UPDATE audit_log SET actor_id = NULL WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("""
                UPDATE audit_log
                SET before = NULL, after = NULL, ip = NULL, user_agent = NULL
                WHERE tenant_id = ?
                """, tenantId);
        // memberships/farms/domain data cascade from tenants where FK declared; explicit cleanup for safety.
        jdbcTemplate.update("DELETE FROM memberships WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM invitations WHERE tenant_id = ?", tenantId);
        tenantRepository.delete(tenant);
        LOG.info("Tenant hard-deleted after retention window: tenantId={}", tenantId);
        auditService.record("TENANT_PURGED", "tenant", tenantId);
    }
}
