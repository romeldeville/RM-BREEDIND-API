package com.rmbreeding.api.sync;

import com.rmbreeding.api.identity.AuthContext;
import com.rmbreeding.api.identity.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncClientServiceTest {

    @Mock
    private SyncClientRepository clientRepository;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    // P13-09 - device cursors

    @Test
    void registersNewDeviceAndStoresCursor() {
        SyncClientService service = new SyncClientService(clientRepository);
        AuthContext.set(new AuthPrincipal(userId, tenantId, List.of("OWNER"), false, null));
        when(clientRepository.findByTenantIdAndDeviceId(tenantId, "device-1"))
                .thenReturn(Optional.empty());

        SyncClientEntity client = service.touch(tenantId, "device-1", 42L);

        assertThat(client.deviceId).isEqualTo("device-1");
        assertThat(client.userId).isEqualTo(userId);
        assertThat(client.lastCursor).isEqualTo(42L);
        assertThat(client.lastSeenAt).isNotNull();
        verify(clientRepository).save(client);
    }

    @Test
    void cursorNeverMovesBackwards() {
        SyncClientService service = new SyncClientService(clientRepository);
        SyncClientEntity existing = new SyncClientEntity();
        existing.id = UUID.randomUUID();
        existing.tenantId = tenantId;
        existing.userId = userId;
        existing.deviceId = "device-1";
        existing.lastCursor = 40L;
        existing.lastSeenAt = Instant.parse("2026-11-10T08:00:00Z");
        when(clientRepository.findByTenantIdAndDeviceId(tenantId, "device-1"))
                .thenReturn(Optional.of(existing));

        service.touch(tenantId, "device-1", 12L);

        ArgumentCaptor<SyncClientEntity> captor = ArgumentCaptor.forClass(SyncClientEntity.class);
        verify(clientRepository).save(captor.capture());
        assertThat(captor.getValue().lastCursor).isEqualTo(40L);
    }
}
