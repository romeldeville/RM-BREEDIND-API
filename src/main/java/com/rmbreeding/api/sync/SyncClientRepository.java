package com.rmbreeding.api.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncClientRepository extends JpaRepository<SyncClientEntity, UUID> {

    Optional<SyncClientEntity> findByTenantIdAndDeviceId(UUID tenantId, String deviceId);

    List<SyncClientEntity> findByTenantIdOrderByLastSeenAtDesc(UUID tenantId);
}
