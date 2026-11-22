package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivacyServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private ConsentService consentService;
    @Mock
    private AuditService auditService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void exportRequiresAuthentication() {
        PrivacyService service = service();
        AuthContext.clear();

        assertThatThrownBy(service::exportCurrentUser)
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void anonymizeRejectsPlatformAdmin() {
        PrivacyService service = service();
        UUID userId = UUID.randomUUID();
        AuthContext.set(new AuthPrincipal(userId, null, List.of(), true, null));
        UserEntity admin = new UserEntity();
        admin.id = userId;
        admin.platformRole = "PLATFORM_ADMIN";
        admin.passwordHash = "hash";
        when(userRepository.findById(userId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.anonymizeCurrentUser(
                new IdentityDtos.AnonymizeRequest("password", "ANONYMISER")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void anonymizeRejectsWrongConfirmationOrPassword() {
        PrivacyService service = service();
        UUID userId = UUID.randomUUID();
        AuthContext.set(new AuthPrincipal(userId, null, List.of(), false, null));
        UserEntity user = new UserEntity();
        user.id = userId;
        user.passwordHash = "hash";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.anonymizeCurrentUser(
                new IdentityDtos.AnonymizeRequest("password", "NOPE")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));

        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);
        assertThatThrownBy(() -> service.anonymizeCurrentUser(
                new IdentityDtos.AnonymizeRequest("bad", "ANONYMISER")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void anonymizeRejectsLastOwner() {
        PrivacyService service = service();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AuthContext.set(new AuthPrincipal(userId, tenantId, List.of("OWNER"), false, null));
        UserEntity user = new UserEntity();
        user.id = userId;
        user.passwordHash = "hash";
        MembershipEntity membership = new MembershipEntity();
        membership.tenantId = tenantId;
        membership.userId = userId;
        membership.role = "OWNER";
        MembershipEntity onlyOwner = new MembershipEntity();
        onlyOwner.role = "OWNER";

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(membership));
        when(membershipRepository.findByTenantIdOrderByCreatedAtAsc(tenantId)).thenReturn(List.of(onlyOwner));

        assertThatThrownBy(() -> service.anonymizeCurrentUser(
                new IdentityDtos.AnonymizeRequest("password", "ANONYMISER")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    private PrivacyService service() {
        return new PrivacyService(userRepository, membershipRepository, tenantRepository,
                refreshTokenRepository, consentService, auditService, passwordEncoder, jdbcTemplate);
    }
}
