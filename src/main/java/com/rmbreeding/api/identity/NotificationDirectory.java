package com.rmbreeding.api.identity;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only directory used by the notification module. It deliberately does not
 * perform an access check so scheduled jobs can run without an authenticated
 * principal; callers must set the tenant context first.
 */
@Service
public class NotificationDirectory {

    private final TenantRepository tenantRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public NotificationDirectory(TenantRepository tenantRepository,
            MembershipRepository membershipRepository,
            UserRepository userRepository) {
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    public List<UUID> tenantIds() {
        return tenantRepository.findAllTenantIds();
    }

    public List<IdentityDtos.NotificationRecipient> recipients(UUID tenantId) {
        List<MembershipEntity> memberships = membershipRepository
                .findByTenantIdOrderByCreatedAtAsc(tenantId).stream()
                .filter(membership -> "ACTIVE".equals(membership.status))
                .toList();
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<UUID, UserEntity> users = userRepository
                .findAllById(memberships.stream().map(membership -> membership.userId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(user -> user.id, Function.identity()));
        return memberships.stream()
                .map(membership -> users.get(membership.userId))
                .filter(user -> user != null && "ACTIVE".equals(user.status) && user.email != null
                        && !user.email.isBlank())
                .map(user -> new IdentityDtos.NotificationRecipient(user.id, user.email, user.fullName))
                .toList();
    }
}
