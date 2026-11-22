package com.rmbreeding.api.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, UUID> {

    List<NotificationLogEntity> findTop50ByTenantIdAndUserIdOrderBySentAtDesc(UUID tenantId, UUID userId);
}
