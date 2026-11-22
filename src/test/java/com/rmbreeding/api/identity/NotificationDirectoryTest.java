package com.rmbreeding.api.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDirectoryTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private UserRepository userRepository;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void listsAllTenantIds() {
        UUID other = UUID.randomUUID();
        when(tenantRepository.findAllTenantIds()).thenReturn(List.of(tenantId, other));

        NotificationDirectory directory = new NotificationDirectory(tenantRepository, membershipRepository,
                userRepository);

        assertThat(directory.tenantIds()).containsExactly(tenantId, other);
    }

    @Test
    void returnsOnlyActiveMembersWithAnEmail() {
        MembershipEntity active = membership(UUID.randomUUID(), "ACTIVE");
        MembershipEntity suspended = membership(UUID.randomUUID(), "SUSPENDED");
        MembershipEntity inactiveUser = membership(UUID.randomUUID(), "ACTIVE");
        when(membershipRepository.findByTenantIdOrderByCreatedAtAsc(tenantId))
                .thenReturn(List.of(active, suspended, inactiveUser));
        when(userRepository.findAllById(anyIds())).thenReturn(List.of(
                user(active.userId, "ACTIVE", "owner@farm.test"),
                user(inactiveUser.userId, "DISABLED", "old@farm.test")));

        NotificationDirectory directory = new NotificationDirectory(tenantRepository, membershipRepository,
                userRepository);

        List<IdentityDtos.NotificationRecipient> recipients = directory.recipients(tenantId);

        assertThat(recipients).hasSize(1);
        assertThat(recipients.get(0).email()).isEqualTo("owner@farm.test");
    }

    @Test
    void skipsUserLookupWhenNoMembershipExists() {
        when(membershipRepository.findByTenantIdOrderByCreatedAtAsc(tenantId)).thenReturn(List.of());

        NotificationDirectory directory = new NotificationDirectory(tenantRepository, membershipRepository,
                userRepository);

        assertThat(directory.recipients(tenantId)).isEmpty();
    }

    private List<UUID> anyIds() {
        return org.mockito.ArgumentMatchers.any();
    }

    private MembershipEntity membership(UUID userId, String status) {
        MembershipEntity entity = new MembershipEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.userId = userId;
        entity.role = "OWNER";
        entity.status = status;
        return entity;
    }

    private UserEntity user(UUID id, String status, String email) {
        UserEntity entity = new UserEntity();
        entity.id = id;
        entity.email = email;
        entity.fullName = "Éleveur";
        entity.status = status;
        return entity;
    }
}
