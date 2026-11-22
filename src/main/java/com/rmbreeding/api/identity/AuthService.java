package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.MfaRequiredException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration TOKEN_TTL = Duration.ofHours(24);
    private static final int MAX_OTP_PER_WINDOW = 5;

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final TenantService tenantService;
    private final TokenService tokenService;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final MfaSecretRepository mfaSecretRepository;
    private final MfaChallengeRepository mfaChallengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationSender sender;
    private final AuditService auditService;
    private final ConsentService consentService;

    public AuthService(UserRepository userRepository, MembershipRepository membershipRepository,
            TenantRepository tenantRepository, TenantService tenantService, TokenService tokenService,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository, OtpCodeRepository otpCodeRepository,
            MfaSecretRepository mfaSecretRepository, MfaChallengeRepository mfaChallengeRepository,
            PasswordEncoder passwordEncoder, VerificationSender sender, AuditService auditService,
            ConsentService consentService) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
        this.tenantService = tenantService;
        this.tokenService = tokenService;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.otpCodeRepository = otpCodeRepository;
        this.mfaSecretRepository = mfaSecretRepository;
        this.mfaChallengeRepository = mfaChallengeRepository;
        this.passwordEncoder = passwordEncoder;
        this.sender = sender;
        this.auditService = auditService;
        this.consentService = consentService;
    }

    @Transactional
    public IdentityDtos.RegisterResponse register(IdentityDtos.RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Email already used");
        }
        if (!request.consent()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Consent is required");
        }
        UserEntity user = newUser(request.email().toLowerCase(), request.phone(), request.fullName());
        user.passwordHash = passwordEncoder.encode(request.password());
        user.locale = request.locale() == null ? "fr" : request.locale();
        userRepository.saveAndFlush(user);

        String slug = slugify(request.fullName());
        if (tenantRepository.existsBySlug(slug)) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        TenantEntity tenant = tenantService.provision(user, request.fullName(), slug, "CI", "XOF");

        consentService.recordRegistrationConsent(user.id);
        createEmailVerification(user);
        auditService.record("USER_REGISTERED", "user", user.id);
        return new IdentityDtos.RegisterResponse(user.id, tenant.id);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationTokenEntity token = emailVerificationTokenRepository
                .findByTokenHash(Hashes.sha256(rawToken))
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.INVALID_CREDENTIALS,
                        "Invalid verification token"));
        if (token.consumedAt != null || token.expiresAt.isBefore(Instant.now())) {
            throw new BusinessRuleException(ErrorCode.EXPIRED, "Verification token expired");
        }
        UserEntity user = userRepository.findById(token.userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        token.consumedAt = Instant.now();
        emailVerificationTokenRepository.save(token);
        user.emailVerifiedAt = Instant.now();
        user.updatedAt = Instant.now();
        userRepository.save(user);
        auditService.record("EMAIL_VERIFIED", "user", user.id);
    }

    @Transactional
    public void requestOtp(IdentityDtos.OtpRequest request) {
        String destination = request.destination().trim();
        boolean email = "EMAIL".equals(request.channel());
        if (email != destination.contains("@")) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Destination does not match the requested channel");
        }
        long recent = otpCodeRepository.countByDestinationAndCreatedAtAfter(destination,
                Instant.now().minus(Duration.ofMinutes(15)));
        if (recent >= MAX_OTP_PER_WINDOW) {
            throw new BusinessRuleException(ErrorCode.RATE_LIMITED, "Too many OTP requests");
        }
        UserEntity user = email
                ? userRepository.findByEmailIgnoreCase(destination).orElseGet(() -> createOtpUser(destination, true))
                : userRepository.findByPhone(destination).orElseGet(() -> createOtpUser(destination, false));

        String code = Hashes.numericCode(6);
        OtpCodeEntity otp = new OtpCodeEntity();
        otp.id = UUID.randomUUID();
        otp.userId = user.id;
        otp.destination = destination;
        otp.channel = request.channel();
        otp.codeHash = Hashes.sha256(code);
        otp.expiresAt = Instant.now().plus(OTP_TTL);
        otp.createdAt = Instant.now();
        otpCodeRepository.save(otp);
        sender.sendOtp(destination, request.channel(), code);
    }

    @Transactional
    public IdentityDtos.TokenPair verifyOtp(IdentityDtos.OtpVerifyRequest request) {
        OtpCodeEntity otp = otpCodeRepository
                .findFirstByDestinationAndConsumedAtIsNullOrderByCreatedAtDesc(request.destination().trim())
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.INVALID_CREDENTIALS, "Invalid OTP"));
        if (otp.expiresAt.isBefore(Instant.now()) || otp.attempts >= 5) {
            throw new BusinessRuleException(ErrorCode.EXPIRED, "OTP expired");
        }
        if (!Hashes.matches(request.code(), otp.codeHash)) {
            otp.attempts++;
            otpCodeRepository.save(otp);
            throw new BusinessRuleException(ErrorCode.INVALID_CREDENTIALS, "Invalid OTP");
        }
        otp.consumedAt = Instant.now();
        otpCodeRepository.save(otp);
        UserEntity user = userRepository.findById(otp.userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return tokenService.issue(user, defaultTenant(user.id));
    }

    @Transactional(noRollbackFor = {BusinessRuleException.class, MfaRequiredException.class})
    public IdentityDtos.TokenPair login(IdentityDtos.LoginRequest request) {
        UserEntity user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> invalidCredentials());
        if (user.isLocked()) {
            throw new BusinessRuleException(ErrorCode.LOCKED, "Account locked, try again later");
        }
        if (user.archivedAt != null || !"ACTIVE".equals(user.status)) {
            throw new BusinessRuleException(ErrorCode.UNAUTHORIZED, "Account is not active");
        }
        if (user.passwordHash == null || !passwordEncoder.matches(request.password(), user.passwordHash)) {
            registerFailedAttempt(user);
            throw invalidCredentials();
        }
        user.failedLoginAttempts = 0;
        user.lockedUntil = null;
        user.updatedAt = Instant.now();
        userRepository.save(user);
        AuthContext.set(new AuthPrincipal(user.id, null, List.of(), user.isPlatformAdmin(), null));

        if (user.isPlatformAdmin() && user.mfaEnabled) {
            String challengeId = createMfaChallenge(user);
            throw new MfaRequiredException(challengeId);
        }
        IdentityDtos.TokenPair tokens = tokenService.issue(user, defaultTenant(user.id));
        auditService.record("USER_LOGIN", "user", user.id);
        return tokens;
    }

    @Transactional
    public IdentityDtos.TokenPair verifyMfa(IdentityDtos.MfaVerifyRequest request) {
        MfaChallengeEntity challenge = mfaChallengeRepository
                .findByIdAndConsumedAtIsNull(UUID.fromString(request.challengeId()))
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.INVALID_CREDENTIALS,
                        "Invalid MFA challenge"));
        if (challenge.expiresAt.isBefore(Instant.now())) {
            throw new BusinessRuleException(ErrorCode.EXPIRED, "MFA challenge expired");
        }
        UserEntity user = userRepository.findById(challenge.userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        MfaSecretEntity secret = mfaSecretRepository.findByUserId(user.id)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.INVALID_CREDENTIALS,
                        "MFA is not configured"));
        if (!Totp.verify(secret.secret, request.code(), Instant.now())) {
            throw new BusinessRuleException(ErrorCode.INVALID_CREDENTIALS, "Invalid TOTP code");
        }
        challenge.consumedAt = Instant.now();
        mfaChallengeRepository.save(challenge);
        AuthContext.set(new AuthPrincipal(user.id, null, List.of(), user.isPlatformAdmin(), null));
        return tokenService.issue(user, defaultTenant(user.id));
    }

    public IdentityDtos.TokenPair refresh(String refreshToken) {
        return tokenService.rotate(refreshToken);
    }

    @Transactional
    public void logout(UUID userId) {
        tokenService.revokeAll(userId);
        auditService.record("USER_LOGOUT", "user", userId);
    }

    public IdentityDtos.MeResponse me(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        List<MembershipEntity> memberships = membershipRepository.findByUserId(userId);
        List<IdentityDtos.MembershipInfo> infos = memberships.stream()
                .map(membership -> new IdentityDtos.MembershipInfo(
                        membership.tenantId,
                        tenantRepository.findById(membership.tenantId).map(t -> t.name).orElse(null),
                        membership.role,
                        List.copyOf(membership.farmScope)))
                .toList();
        return new IdentityDtos.MeResponse(user.id, user.email, user.phone, user.fullName, user.locale,
                user.isPlatformAdmin(), infos);
    }

    @Transactional
    public IdentityDtos.MeResponse updateProfile(UUID userId, IdentityDtos.UpdateProfileRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.fullName = request.fullName().trim();
        }
        if (request.phone() != null) {
            user.phone = request.phone().trim().isEmpty() ? null : request.phone().trim();
        }
        if (request.locale() != null && !request.locale().isBlank()) {
            user.locale = request.locale().trim();
        }
        user.updatedAt = Instant.now();
        userRepository.save(user);
        auditService.record("PROFILE_UPDATED", "user", user.id);
        return me(userId);
    }

    @Transactional
    public void changePassword(UUID userId, IdentityDtos.ChangePasswordRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.passwordHash == null || !passwordEncoder.matches(request.currentPassword(), user.passwordHash)) {
            throw new BusinessRuleException(ErrorCode.INVALID_CREDENTIALS, "Current password does not match");
        }
        user.passwordHash = passwordEncoder.encode(request.newPassword());
        user.updatedAt = Instant.now();
        userRepository.save(user);
        auditService.record("PASSWORD_CHANGED", "user", user.id);
    }

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmailIgnoreCase(email.trim()).ifPresent(user -> {
            String raw = Hashes.randomToken();
            PasswordResetTokenEntity token = new PasswordResetTokenEntity();
            token.id = UUID.randomUUID();
            token.userId = user.id;
            token.tokenHash = Hashes.sha256(raw);
            token.expiresAt = Instant.now().plus(Duration.ofHours(1));
            token.createdAt = Instant.now();
            passwordResetTokenRepository.save(token);
            sender.sendPasswordReset(user.email, user.fullName, raw);
        });
    }

    @Transactional
    public void resetPassword(IdentityDtos.PasswordResetRequest request) {
        PasswordResetTokenEntity token = passwordResetTokenRepository
                .findByTokenHash(Hashes.sha256(request.token()))
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.INVALID_CREDENTIALS,
                        "Invalid reset token"));
        if (token.consumedAt != null || token.expiresAt.isBefore(Instant.now())) {
            throw new BusinessRuleException(ErrorCode.EXPIRED, "Reset token expired");
        }
        UserEntity user = userRepository.findById(token.userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        token.consumedAt = Instant.now();
        passwordResetTokenRepository.save(token);
        user.passwordHash = passwordEncoder.encode(request.password());
        user.updatedAt = Instant.now();
        userRepository.save(user);
        tokenService.revokeAll(user.id);
        auditService.record("PASSWORD_RESET", "user", user.id);
    }

    @Transactional
    public IdentityDtos.MfaSetupResponse setupMfa(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        MfaSecretEntity secret = mfaSecretRepository.findByUserId(userId).orElseGet(() -> {
            MfaSecretEntity created = new MfaSecretEntity();
            created.id = UUID.randomUUID();
            created.userId = userId;
            created.createdAt = Instant.now();
            return created;
        });
        secret.secret = Totp.generateSecret();
        secret.enabled = false;
        secret.updatedAt = Instant.now();
        mfaSecretRepository.save(secret);
        String uri = "otpauth://totp/RM%20Breeding:" + user.email + "?secret=" + secret.secret
                + "&issuer=RM%20Breeding";
        return new IdentityDtos.MfaSetupResponse(secret.secret, uri);
    }

    @Transactional
    public void enableMfa(UUID userId, String code) {
        MfaSecretEntity secret = mfaSecretRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "MFA setup required"));
        if (!Totp.verify(secret.secret, code, Instant.now())) {
            throw new BusinessRuleException(ErrorCode.INVALID_CREDENTIALS, "Invalid TOTP code");
        }
        secret.enabled = true;
        secret.updatedAt = Instant.now();
        mfaSecretRepository.save(secret);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.mfaEnabled = true;
        user.updatedAt = Instant.now();
        userRepository.save(user);
        auditService.record("MFA_ENABLED", "user", userId);
    }

    @Transactional
    public IdentityDtos.TokenPair changeTenant(UUID userId, UUID tenantId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        MembershipEntity membership = membershipRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.FORBIDDEN,
                        "User is not a member of this tenant"));
        if (!"ACTIVE".equals(membership.status)) {
            throw new BusinessRuleException(ErrorCode.FORBIDDEN, "Membership is not active");
        }
        AuthContext.set(new AuthPrincipal(user.id, tenantId, List.of(membership.role),
                user.isPlatformAdmin(), null));
        IdentityDtos.TokenPair tokens = tokenService.issue(user, tenantId);
        auditService.record("TENANT_SWITCHED", "tenant", tenantId);
        return tokens;
    }

    private UserEntity newUser(String email, String phone, String fullName) {
        Instant now = Instant.now();
        UserEntity user = new UserEntity();
        user.id = UUID.randomUUID();
        user.email = email;
        user.phone = phone == null || phone.isBlank() ? null : phone;
        user.fullName = fullName;
        user.status = "ACTIVE";
        user.locale = "fr";
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    private UserEntity createOtpUser(String destination, boolean email) {
        UserEntity user = newUser(email ? destination.toLowerCase() : null, email ? null : destination,
                email ? destination.substring(0, destination.indexOf('@')) : destination);
        return userRepository.save(user);
    }

    private void createEmailVerification(UserEntity user) {
        String raw = Hashes.randomToken();
        EmailVerificationTokenEntity token = new EmailVerificationTokenEntity();
        token.id = UUID.randomUUID();
        token.userId = user.id;
        token.tokenHash = Hashes.sha256(raw);
        token.expiresAt = Instant.now().plus(TOKEN_TTL);
        token.createdAt = Instant.now();
        emailVerificationTokenRepository.save(token);
        sender.sendEmailVerification(user.email, user.fullName, raw);
    }

    private String createMfaChallenge(UserEntity user) {
        MfaChallengeEntity challenge = new MfaChallengeEntity();
        challenge.id = UUID.randomUUID();
        challenge.userId = user.id;
        challenge.codeHash = "TOTP";
        challenge.expiresAt = Instant.now().plus(OTP_TTL);
        challenge.createdAt = Instant.now();
        mfaChallengeRepository.save(challenge);
        return challenge.id.toString();
    }

    private void registerFailedAttempt(UserEntity user) {
        user.failedLoginAttempts = user.failedLoginAttempts + 1;
        if (user.failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            user.lockedUntil = Instant.now().plus(LOCK_DURATION);
        }
        user.updatedAt = Instant.now();
        userRepository.save(user);
    }

    private UUID defaultTenant(UUID userId) {
        return membershipRepository.findByUserId(userId).stream()
                .filter(membership -> "ACTIVE".equals(membership.status))
                .map(membership -> membership.tenantId)
                .findFirst()
                .orElse(null);
    }

    private BusinessRuleException invalidCredentials() {
        return new BusinessRuleException(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials");
    }

    private String slugify(String value) {
        String slug = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "farm-" + UUID.randomUUID().toString().substring(0, 8) : slug;
    }
}
