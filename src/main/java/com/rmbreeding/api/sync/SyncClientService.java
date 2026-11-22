package com.rmbreeding.api.sync;

import com.rmbreeding.api.identity.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class SyncClientService {

    private static final UUID SYSTEM_USER = new UUID(0L, 0L);

    private final SyncClientRepository clientRepository;

    public SyncClientService(SyncClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    /**
     * Register the device and advance its cursor. A cursor never moves backwards.
     */
    @Transactional
    public SyncClientEntity touch(UUID tenantId, String deviceId, long cursor) {
        SyncClientEntity client = clientRepository.findByTenantIdAndDeviceId(tenantId, deviceId)
                .orElseGet(() -> {
                    SyncClientEntity created = new SyncClientEntity();
                    created.id = UUID.randomUUID();
                    created.tenantId = tenantId;
                    created.deviceId = deviceId;
                    created.lastCursor = 0;
                    return created;
                });
        UUID userId = AuthContext.userId();
        client.userId = userId != null ? userId : SYSTEM_USER;
        client.lastCursor = Math.max(client.lastCursor, Math.max(cursor, 0));
        client.lastSeenAt = Instant.now();
        clientRepository.save(client);
        return client;
    }

    public Optional<SyncClientEntity> find(UUID tenantId, String deviceId) {
        return clientRepository.findByTenantIdAndDeviceId(tenantId, deviceId);
    }
}
