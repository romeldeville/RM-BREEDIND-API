package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * P16-03 / P16-04 - export des données personnelles et anonymisation.
 */
@Service
public class PrivacyService {

    private static final String ANONYMIZED_NAME = "Utilisateur anonymisé";
    private static final String ANONYMIZATION_CONFIRMATION = "ANONYMISER";

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ConsentService consentService;
    private final AuditService auditService;

    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public PrivacyService(UserRepository userRepository, MembershipRepository membershipRepository,
            TenantRepository tenantRepository, RefreshTokenRepository refreshTokenRepository,
            ConsentService consentService, AuditService auditService,
            PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.consentService = consentService;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PrivacyDtos.PersonalDataExport exportCurrentUser() {
        UUID userId = requireCurrentUser();
        UserEntity user = requireUser(userId);
        List<MembershipEntity> memberships = membershipRepository.findByUserId(userId);
        List<PrivacyDtos.Membership> membershipViews = memberships.stream()
                .map(this::membershipView)
                .toList();
        List<AuditDtos.AuditEntryResponse> auditTrail = new ArrayList<>();
        for (MembershipEntity membership : memberships) {
            auditTrail.addAll(auditService.list(membership.tenantId, null, null, null, null, 200)
                    .stream()
                    .filter(entry -> userId.equals(entry.actorId()))
                    .toList());
        }
        auditTrail.sort((left, right) -> right.occurredAt().compareTo(left.occurredAt()));
        PrivacyDtos.Profile profile = new PrivacyDtos.Profile(user.id, user.email, user.fullName,
                user.phone, user.locale, user.status, user.mfaEnabled, user.emailVerifiedAt,
                user.phoneVerifiedAt, user.createdAt, user.updatedAt);
        return new PrivacyDtos.PersonalDataExport(profile, membershipViews,
                consentService.list(userId), List.copyOf(auditTrail), Instant.now(), "1.0");
    }

    @Transactional
    public void anonymizeCurrentUser(IdentityDtos.AnonymizeRequest request) {
        UUID userId = requireCurrentUser();
        UserEntity user = requireUser(userId);
        if (user.isPlatformAdmin()) {
            throw new BusinessRuleException(ErrorCode.FORBIDDEN,
                    "Platform administrators cannot be anonymized through this endpoint");
        }
        if (!ANONYMIZATION_CONFIRMATION.equalsIgnoreCase(request.confirmation().trim())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Confirmation must be " + ANONYMIZATION_CONFIRMATION);
        }
        if (user.passwordHash == null || !passwordEncoder.matches(request.password(), user.passwordHash)) {
            throw new BusinessRuleException(ErrorCode.INVALID_CREDENTIALS, "Invalid password");
        }

        List<MembershipEntity> memberships = membershipRepository.findByUserId(userId);
        for (MembershipEntity membership : memberships) {
            if ("OWNER".equals(membership.role) && ownerCount(membership.tenantId) <= 1) {
                throw new BusinessRuleException(ErrorCode.CONFLICT,
                        "Transfer tenant ownership before anonymizing this account");
            }
        }

        Map<String, Object> before = maskedUserSnapshot(user);
        for (MembershipEntity membership : memberships) {
            eraseTenantScopedUserData(membership.tenantId, userId);
            membershipRepository.delete(membership);
        }
        refreshTokenRepository.deleteAll(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId));
        jdbcTemplate.update("DELETE FROM otp_codes WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM email_verification_tokens WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM mfa_secrets WHERE user_id = ?", userId);

        Instant now = Instant.now();
        user.email = null;
        user.phone = null;
        user.passwordHash = null;
        user.fullName = ANONYMIZED_NAME;
        user.locale = "fr";
        user.status = "ANONYMIZED";
        user.mfaEnabled = false;
        user.failedLoginAttempts = 0;
        user.lockedUntil = null;
        user.archivedAt = now;
        user.updatedAt = now;
        userRepository.saveAndFlush(user);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", "ANONYMIZED");
        after.put("email", "erased");
        after.put("phone", "erased");
        after.put("fullName", "erased");
        after.put("memberships", "removed");
        auditService.recordChange("USER_ANONYMIZED", "user", userId, before, after);
    }

    public List<IdentityDtos.ConsentResponse> consents() {
        return consentService.list(requireCurrentUser());
    }

    private void eraseTenantScopedUserData(UUID tenantId, UUID userId) {
        for (String table : List.of("push_tokens", "notification_preferences", "notification_log")) {
            jdbcTemplate.update("""
                    WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                    DELETE FROM %s USING ctx WHERE user_id = ?
                    """.formatted(table), tenantId.toString(), userId);
        }
    }

    private long ownerCount(UUID tenantId) {
        return membershipRepository.findByTenantIdOrderByCreatedAtAsc(tenantId).stream()
                .filter(membership -> "OWNER".equals(membership.role))
                .count();
    }

    private PrivacyDtos.Membership membershipView(MembershipEntity membership) {
        String tenantName = tenantRepository.findById(membership.tenantId)
                .map(tenant -> tenant.name)
                .orElse(null);
        return new PrivacyDtos.Membership(membership.tenantId, tenantName, membership.role,
                membership.status, List.copyOf(membership.farmScope), membership.createdAt);
    }

    private Map<String, Object> maskedUserSnapshot(UserEntity user) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("email", maskEmail(user.email));
        snapshot.put("phone", user.phone == null ? null : "***");
        snapshot.put("fullName", user.fullName == null ? null : "***");
        snapshot.put("status", user.status);
        snapshot.put("mfaEnabled", user.mfaEnabled);
        return snapshot;
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private UUID requireCurrentUser() {
        UUID userId = AuthContext.userId();
        if (userId == null) {
            throw new BusinessRuleException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return userId;
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
