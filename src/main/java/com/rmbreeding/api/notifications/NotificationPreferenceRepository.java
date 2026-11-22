package com.rmbreeding.api.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreferenceEntity, UUID> {

    Optional<NotificationPreferenceEntity> findByUserIdAndTypeAndChannel(UUID userId, String type, String channel);

    Optional<NotificationPreferenceEntity> findByTenantIdAndUserIdAndTypeAndChannel(UUID tenantId, UUID userId,
            String type, String channel);

    List<NotificationPreferenceEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}
