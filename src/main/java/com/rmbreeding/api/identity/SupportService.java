package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class SupportService {

    private static final java.time.Duration SESSION_DURATION = java.time.Duration.ofHours(2);

    private final SupportSessionRepository supportSessionRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    public SupportService(SupportSessionRepository supportSessionRepository, UserRepository userRepository,
            TokenService tokenService, AuditService auditService, JdbcTemplate jdbcTemplate) {
        this.supportSessionRepository = supportSessionRepository;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public IdentityDtos.TokenPair start(UUID adminId, UUID tenantId, String reason) {
        UserEntity admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (!admin.isPlatformAdmin() || !admin.mfaEnabled) {
            throw new com.rmbreeding.api.shared.BusinessRuleException(
                    com.rmbreeding.api.shared.ErrorCode.FORBIDDEN,
                    "Platform admin with MFA is required for support mode");
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(SESSION_DURATION);
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                INSERT INTO support_sessions (id, platform_admin_id, tenant_id, reason, started_at, expires_at)
                SELECT ?, ?, ?, ?, ?, ? FROM ctx
                """, tenantId.toString(), sessionId, adminId, tenantId, reason,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(expiresAt));
        auditService.record("SUPPORT_SESSION_STARTED", "tenant", tenantId, null, null, sessionId);
        return tokenService.issue(admin, tenantId, null, sessionId);
    }

    @Transactional
    public void end(UUID adminId, UUID sessionId) {
        SupportSessionEntity session = supportSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Support session not found"));
        if (!session.platformAdminId.equals(adminId)) {
            throw new com.rmbreeding.api.shared.BusinessRuleException(
                    com.rmbreeding.api.shared.ErrorCode.FORBIDDEN, "Not your support session");
        }
        if (session.endedAt == null) {
            session.endedAt = Instant.now();
            supportSessionRepository.save(session);
            tokenService.revokeSupportSession(sessionId);
        }
        auditService.record("SUPPORT_SESSION_ENDED", "tenant", session.tenantId, null, null, sessionId);
    }
}
