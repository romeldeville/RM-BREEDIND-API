package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final QuotaService quotaService;
    private final PasswordEncoder passwordEncoder;
    private final VerificationSender sender;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    public InvitationService(InvitationRepository invitationRepository,
            MembershipRepository membershipRepository, UserRepository userRepository,
            TenantRepository tenantRepository, QuotaService quotaService, PasswordEncoder passwordEncoder,
            VerificationSender sender, AuditService auditService, JdbcTemplate jdbcTemplate) {
        this.invitationRepository = invitationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.quotaService = quotaService;
        this.passwordEncoder = passwordEncoder;
        this.sender = sender;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public IdentityDtos.InvitationResponse invite(UUID tenantId, IdentityDtos.InviteMemberRequest request,
            UUID invitedBy) {
        FarmService.requireTenantAccess(tenantId);
        AuthPrincipal principal = AuthContext.get();
        boolean allowed = principal != null && (principal.platformAdmin()
                || principal.hasRole("OWNER") || principal.hasRole("MANAGER"));
        if (!allowed) {
            throw new BusinessRuleException(ErrorCode.FORBIDDEN, "Insufficient role to invite members");
        }
        long members = membershipRepository.countByTenantId(tenantId);
        quotaService.requireCapacity(tenantId, "members", members);

        String rawToken = Hashes.randomToken();
        Instant now = Instant.now();
        InvitationEntity invitation = new InvitationEntity();
        invitation.id = UUID.randomUUID();
        invitation.tenantId = tenantId;
        invitation.email = request.email().toLowerCase();
        invitation.role = request.role();
        invitation.farmScope = request.farmScope() == null ? List.of() : request.farmScope();
        invitation.tokenHash = Hashes.sha256(rawToken);
        invitation.expiresAt = now.plus(Duration.ofDays(7));
        invitation.invitedBy = invitedBy;
        invitation.createdAt = now;
        invitationRepository.save(invitation);
        sender.sendEmailVerification(invitation.email, null, rawToken);
        auditService.record("INVITATION_SENT", "invitation", invitation.id);
        return toResponse(invitation);
    }

    @Transactional
    public void accept(IdentityDtos.AcceptInvitationRequest request) {
        InvitationEntity invitation = invitationRepository.findByTokenHash(Hashes.sha256(request.token()))
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.INVALID_CREDENTIALS,
                        "Invalid invitation token"));
        if (invitation.acceptedAt != null) {
            throw new BusinessRuleException(ErrorCode.CONFLICT, "Invitation already accepted");
        }
        if (invitation.expiresAt.isBefore(Instant.now())) {
            throw new BusinessRuleException(ErrorCode.EXPIRED, "Invitation expired");
        }
        if (userRepository.existsByEmailIgnoreCase(invitation.email)) {
            throw new ConflictException("An account already exists for this email");
        }
        Instant now = Instant.now();
        UserEntity user = new UserEntity();
        user.id = UUID.randomUUID();
        user.email = invitation.email;
        user.fullName = request.fullName();
        user.passwordHash = passwordEncoder.encode(request.password());
        user.status = "ACTIVE";
        user.locale = "fr";
        user.emailVerifiedAt = now;
        user.createdAt = now;
        user.updatedAt = now;
        userRepository.saveAndFlush(user);

        jdbcTemplate.update("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                INSERT INTO memberships (id, tenant_id, user_id, role, farm_scope)
                SELECT ?, ?, ?, ?, ?::uuid[] FROM ctx
                """, invitation.tenantId.toString(), UUID.randomUUID(), invitation.tenantId, user.id,
                invitation.role, toPgArray(invitation.farmScope));

        invitation.acceptedAt = now;
        invitationRepository.save(invitation);
        quotaService.setUsed(invitation.tenantId, "members",
                (int) membershipRepository.countByTenantId(invitation.tenantId));
        auditService.record("INVITATION_ACCEPTED", "invitation", invitation.id);
    }

    public List<IdentityDtos.InvitationResponse> list(UUID tenantId) {
        FarmService.requireTenantAccess(tenantId);
        return invitationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    public void sendInvitationEmail(String email, String token) {
        sender.sendEmailVerification(email, null, token);
    }

    private String toPgArray(List<UUID> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        return "{" + String.join(",", values.stream().map(UUID::toString).toList()) + "}";
    }

    private IdentityDtos.InvitationResponse toResponse(InvitationEntity invitation) {
        return new IdentityDtos.InvitationResponse(invitation.id, invitation.email, invitation.role,
                invitation.acceptedAt == null ? "PENDING" : "ACCEPTED", invitation.expiresAt);
    }
}
