package com.rmbreeding.api.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushTokenRepository extends JpaRepository<PushTokenEntity, UUID> {

    Optional<PushTokenEntity> findByToken(String token);

    List<PushTokenEntity> findByTenantId(UUID tenantId);

    List<PushTokenEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    void deleteByToken(String token);
}
