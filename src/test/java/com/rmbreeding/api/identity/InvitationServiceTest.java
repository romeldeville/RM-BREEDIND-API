package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private InvitationRepository invitationRepository;
    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private QuotaService quotaService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private VerificationSender sender;
    @Mock
    private AuditService auditService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private InvitationService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InvitationService(invitationRepository, membershipRepository, userRepository,
                tenantRepository, quotaService, passwordEncoder, sender, auditService, jdbcTemplate);
        AuthContext.set(new AuthPrincipal(userId, tenantId, List.of("OWNER"), false, null));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void inviteCreatesHashedInvitationAndSendsEmail() {
        when(membershipRepository.countByTenantId(tenantId)).thenReturn(1L);
        when(invitationRepository.save(any(InvitationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        IdentityDtos.InvitationResponse response = service.invite(tenantId,
                new IdentityDtos.InviteMemberRequest("New@RMbreeding.local", "WORKER", List.of()),
                userId);

        assertThat(response.email()).isEqualTo("new@rmbreeding.local");
        assertThat(response.role()).isEqualTo("WORKER");
        assertThat(response.status()).isEqualTo("PENDING");

        ArgumentCaptor<InvitationEntity> saved = ArgumentCaptor.forClass(InvitationEntity.class);
        verify(invitationRepository).save(saved.capture());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(sender).sendEmailVerification(eq("new@rmbreeding.local"), eq(null), token.capture());
        assertThat(token.getValue()).isNotBlank();
        assertThat(saved.getValue().tokenHash).isEqualTo(Hashes.sha256(token.getValue()));
        verify(quotaService).requireCapacity(tenantId, "members", 1L);
        verify(auditService).record("INVITATION_SENT", "invitation", response.id());
    }

    @Test
    void inviteRejectsInsufficientRole() {
        AuthContext.set(new AuthPrincipal(userId, tenantId, List.of("WORKER"), false, null));

        assertThatThrownBy(() -> service.invite(tenantId,
                new IdentityDtos.InviteMemberRequest("new@rmbreeding.local", "WORKER", null), userId))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void listMapsPendingAndAcceptedInvitations() {
        InvitationEntity pending = invitation("pending@rmbreeding.local");
        InvitationEntity accepted = invitation("accepted@rmbreeding.local");
        accepted.acceptedAt = Instant.now();
        when(invitationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId))
                .thenReturn(List.of(pending, accepted));

        List<IdentityDtos.InvitationResponse> invitations = service.list(tenantId);

        assertThat(invitations).extracting(IdentityDtos.InvitationResponse::status)
                .containsExactly("PENDING", "ACCEPTED");
    }

    @Test
    void acceptCreatesUserMembershipAndAudits() {
        String rawToken = "invitation-token";
        InvitationEntity invitation = invitation("invitee@rmbreeding.local");
        invitation.tokenHash = Hashes.sha256(rawToken);
        UUID farmId = UUID.randomUUID();
        invitation.farmScope = new ArrayList<>(List.of(farmId));
        when(invitationRepository.findByTokenHash(Hashes.sha256(rawToken))).thenReturn(Optional.of(invitation));
        when(userRepository.existsByEmailIgnoreCase(invitation.email)).thenReturn(false);
        when(passwordEncoder.encode("SecretPassword123!")).thenReturn("encoded");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(membershipRepository.countByTenantId(tenantId)).thenReturn(2L);

        service.accept(new IdentityDtos.AcceptInvitationRequest(rawToken, "SecretPassword123!", "Invitee"));

        ArgumentCaptor<UserEntity> user = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(user.capture());
        assertThat(user.getValue().email).isEqualTo("invitee@rmbreeding.local");
        assertThat(user.getValue().passwordHash).isEqualTo("encoded");
        assertThat(user.getValue().status).isEqualTo("ACTIVE");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(), any(), any(), any(), any(), any());
        assertThat(sql.getValue()).contains("INSERT INTO memberships");
        assertThat(invitation.acceptedAt).isNotNull();
        verify(quotaService).setUsed(tenantId, "members", 2);
        verify(auditService).record("INVITATION_ACCEPTED", "invitation", invitation.id);
    }

    @Test
    void acceptRejectsUnknownToken() {
        when(invitationRepository.findByTokenHash(Hashes.sha256("unknown"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(
                new IdentityDtos.AcceptInvitationRequest("unknown", "SecretPassword123!", "Invitee")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void acceptRejectsAlreadyAcceptedInvitation() {
        InvitationEntity invitation = invitation("invitee@rmbreeding.local");
        invitation.acceptedAt = Instant.now();
        when(invitationRepository.findByTokenHash(Hashes.sha256("token"))).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.accept(
                new IdentityDtos.AcceptInvitationRequest("token", "SecretPassword123!", "Invitee")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void acceptRejectsExpiredInvitation() {
        InvitationEntity invitation = invitation("invitee@rmbreeding.local");
        invitation.expiresAt = Instant.now().minusSeconds(60);
        when(invitationRepository.findByTokenHash(Hashes.sha256("token"))).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.accept(
                new IdentityDtos.AcceptInvitationRequest("token", "SecretPassword123!", "Invitee")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.EXPIRED));
    }

    @Test
    void acceptRejectsExistingAccount() {
        InvitationEntity invitation = invitation("invitee@rmbreeding.local");
        when(invitationRepository.findByTokenHash(Hashes.sha256("token"))).thenReturn(Optional.of(invitation));
        when(userRepository.existsByEmailIgnoreCase(invitation.email)).thenReturn(true);

        assertThatThrownBy(() -> service.accept(
                new IdentityDtos.AcceptInvitationRequest("token", "SecretPassword123!", "Invitee")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("account already exists");
    }

    @Test
    void sendInvitationEmailDelegatesToSender() {
        service.sendInvitationEmail("invitee@rmbreeding.local", "token");

        verify(sender).sendEmailVerification("invitee@rmbreeding.local", null, "token");
    }

    private InvitationEntity invitation(String email) {
        InvitationEntity invitation = new InvitationEntity();
        invitation.id = UUID.randomUUID();
        invitation.tenantId = tenantId;
        invitation.email = email;
        invitation.role = "WORKER";
        invitation.farmScope = new ArrayList<>();
        invitation.tokenHash = Hashes.sha256("token");
        invitation.expiresAt = Instant.now().plusSeconds(3600);
        invitation.invitedBy = userId;
        invitation.createdAt = Instant.now();
        return invitation;
    }
}
