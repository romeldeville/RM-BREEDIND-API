package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.JwtProperties;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TokenService {

    private final JwtService jwtService;
    private final JwtProperties properties;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SupportSessionRepository supportSessionRepository;

    public TokenService(JwtService jwtService, JwtProperties properties, UserRepository userRepository,
            MembershipRepository membershipRepository, RefreshTokenRepository refreshTokenRepository,
            SupportSessionRepository supportSessionRepository) {
        this.jwtService = jwtService;
        this.properties = properties;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.supportSessionRepository = supportSessionRepository;
    }

    @Transactional
    public IdentityDtos.TokenPair issue(UserEntity user, UUID tenantId, UUID rotatedFrom) {
        return issue(user, tenantId, rotatedFrom, null);
    }

    @Transactional
    public IdentityDtos.TokenPair issue(UserEntity user, UUID tenantId, UUID rotatedFrom,
            UUID supportSessionId) {
        if (tenantId != null) {
            TenantContext.setTenantId(tenantId);
        }
        SupportSessionEntity supportSession = null;
        if (supportSessionId != null) {
            supportSession = requireActiveSupportSession(supportSessionId, user.id, tenantId);
        }
        List<String> roles = new ArrayList<>();
        if (tenantId != null) {
            membershipRepository.findByTenantIdAndUserId(tenantId, user.id)
                    .ifPresent(membership -> roles.add(membership.role));
        }
        if (user.isPlatformAdmin()) {
            roles.add("PLATFORM_ADMIN");
        }
        String accessToken = jwtService.issue(user.id, tenantId, roles, user.isPlatformAdmin(),
                supportSessionId);
        String rawRefresh = Hashes.randomToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getRefreshTokenTtl());
        if (supportSession != null && supportSession.expiresAt.isBefore(expiresAt)) {
            expiresAt = supportSession.expiresAt;
        }
        RefreshTokenEntity refresh = new RefreshTokenEntity();
        refresh.id = UUID.randomUUID();
        refresh.userId = user.id;
        refresh.tenantId = tenantId;
        refresh.tokenHash = Hashes.sha256(rawRefresh);
        refresh.expiresAt = expiresAt;
        refresh.rotatedFrom = rotatedFrom;
        refresh.supportSessionId = supportSessionId;
        refresh.createdAt = now;
        refreshTokenRepository.save(refresh);
        return new IdentityDtos.TokenPair(accessToken, rawRefresh, jwtService.accessTokenTtlSeconds(), "Bearer");
    }

    public IdentityDtos.TokenPair issue(UserEntity user, UUID tenantId) {
        return issue(user, tenantId, null);
    }

    @Transactional
    public IdentityDtos.TokenPair rotate(String rawRefreshToken) {
        RefreshTokenEntity current = refreshTokenRepository.findByTokenHash(Hashes.sha256(rawRefreshToken))
                .orElseThrow(() -> new com.rmbreeding.api.shared.BusinessRuleException(
                        com.rmbreeding.api.shared.ErrorCode.UNAUTHORIZED, "Invalid refresh token"));
        if (current.revokedAt != null || current.expiresAt.isBefore(Instant.now())) {
            throw new com.rmbreeding.api.shared.BusinessRuleException(
                    com.rmbreeding.api.shared.ErrorCode.UNAUTHORIZED, "Refresh token expired or revoked");
        }
        if (current.supportSessionId != null) {
            requireActiveSupportSession(current.supportSessionId, current.userId, current.tenantId);
        }
        UserEntity user = userRepository.findById(current.userId)
                .orElseThrow(() -> new com.rmbreeding.api.shared.BusinessRuleException(
                        com.rmbreeding.api.shared.ErrorCode.UNAUTHORIZED, "Invalid refresh token"));
        if (user.archivedAt != null || !"ACTIVE".equals(user.status)) {
            throw new com.rmbreeding.api.shared.BusinessRuleException(
                    com.rmbreeding.api.shared.ErrorCode.UNAUTHORIZED, "Account is not active");
        }
        current.revokedAt = Instant.now();
        refreshTokenRepository.save(current);
        AuthContext.set(new AuthPrincipal(user.id, current.tenantId, List.of(), user.isPlatformAdmin(),
                current.supportSessionId));
        return issue(user, current.tenantId, current.id, current.supportSessionId);
    }

    @Transactional
    public void revokeSupportSession(UUID supportSessionId) {
        Instant now = Instant.now();
        for (RefreshTokenEntity token : refreshTokenRepository
                .findBySupportSessionIdAndRevokedAtIsNull(supportSessionId)) {
            token.revokedAt = now;
            refreshTokenRepository.save(token);
        }
    }

    private SupportSessionEntity requireActiveSupportSession(UUID sessionId, UUID userId, UUID tenantId) {
        SupportSessionEntity session = supportSessionRepository.findById(sessionId)
                .orElseThrow(() -> new com.rmbreeding.api.shared.BusinessRuleException(
                        com.rmbreeding.api.shared.ErrorCode.UNAUTHORIZED, "Support session not found"));
        if (session.endedAt != null || session.expiresAt.isBefore(Instant.now())
                || !session.platformAdminId.equals(userId)
                || (tenantId != null && !tenantId.equals(session.tenantId))) {
            throw new com.rmbreeding.api.shared.BusinessRuleException(
                    com.rmbreeding.api.shared.ErrorCode.UNAUTHORIZED, "Support session expired or inactive");
        }
        return session;
    }

    @Transactional
    public void revokeAll(UUID userId) {
        for (RefreshTokenEntity token : refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId)) {
            token.revokedAt = Instant.now();
            refreshTokenRepository.save(token);
        }
    }
}
