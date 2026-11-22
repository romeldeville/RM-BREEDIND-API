package com.rmbreeding.api.sync;

import com.rmbreeding.api.alerts.AlertsService;
import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncConflictServiceTest {

    @Mock
    private SyncConflictRepository conflictRepository;
    @Mock
    private SyncProjectionService projectionService;
    @Mock
    private AlertsService alertsService;
    @Mock
    private AuditService auditService;

    private SyncConflictService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID farmId = UUID.randomUUID();
    private final UUID aggregateId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SyncConflictService(conflictRepository, projectionService, alertsService,
                auditService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // P13-07

    @Test
    void recordsConflictAndRaisesCriticalAlert() {
        DomainEventEntity event = event();
        SyncProjectionEntity projection = new SyncProjectionEntity();
        projection.eventId = UUID.randomUUID();
        SyncProjectionService.ConflictSignal signal =
                new SyncProjectionService.ConflictSignal("HEADCOUNT_MISMATCH", 20, 18);

        SyncConflictEntity conflict = service.record(event, projection, signal);

        assertThat(conflict.entityId).isEqualTo(aggregateId);
        assertThat(conflict.serverEventId).isEqualTo(projection.eventId);
        assertThat(conflict.payload).containsEntry("reason", "HEADCOUNT_MISMATCH");
        assertThat(conflict.payload).containsKey("incoming");
        verify(conflictRepository).save(conflict);
        verify(alertsService).raise(eq(tenantId), eq(farmId), eq("SYNC_CONFLICT"), eq("CRITICAL"),
                eq("Conflit de synchronisation"), any(), any(), eq("sync_conflict"), eq(conflict.id));
        verify(auditService).record(eq("SYNC_CONFLICT_DETECTED"), eq("sync_conflict"), eq(conflict.id));
    }

    @Test
    void skipsAlertWithoutFarm() {
        DomainEventEntity event = event();
        event.farmId = null;
        SyncProjectionService.ConflictSignal signal =
                new SyncProjectionService.ConflictSignal("HEADCOUNT_MISMATCH", 20, 18);

        service.record(event, null, signal);

        verify(alertsService, never()).raise(any(), any(), any(), any(), any(), any(), any(),
                any(), any());
    }

    // P13-08

    @Test
    void resolvesWithClientValueAndClosesAlert() {
        SyncConflictEntity conflict = conflict();
        when(conflictRepository.findByIdAndTenantId(conflict.id, tenantId))
                .thenReturn(Optional.of(conflict));

        SyncConflictEntity resolved = service.resolve(conflict.id,
                new SyncDtos.ResolveConflictRequest("CLIENT_WINS", Map.of("headcount", 21)));

        assertThat(resolved.resolution).isEqualTo("CLIENT_WINS");
        assertThat(resolved.resolvedAt).isNotNull();
        verify(projectionService).applyResolution(conflict, Map.of("headcount", 21));
        verify(alertsService).autoClose(tenantId, "sync_conflict", conflict.id);
        verify(auditService).record("SYNC_CONFLICT_RESOLVED", "sync_conflict", conflict.id);
    }

    @Test
    void resolvesWithServerValueWithoutTouchingProjection() {
        SyncConflictEntity conflict = conflict();
        when(conflictRepository.findByIdAndTenantId(conflict.id, tenantId))
                .thenReturn(Optional.of(conflict));

        service.resolve(conflict.id, new SyncDtos.ResolveConflictRequest("SERVER_WINS", null));

        verify(projectionService, never()).applyResolution(any(), any());
        verify(auditService).record("SYNC_CONFLICT_RESOLVED", "sync_conflict", conflict.id);
    }

    @Test
    void rejectsDoubleResolution() {
        SyncConflictEntity conflict = conflict();
        conflict.resolvedAt = Instant.now();
        conflict.resolution = "SERVER_WINS";
        when(conflictRepository.findByIdAndTenantId(conflict.id, tenantId))
                .thenReturn(Optional.of(conflict));

        assertThatThrownBy(() -> service.resolve(conflict.id,
                new SyncDtos.ResolveConflictRequest("CLIENT_WINS", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void listsOnlyOpenConflictsByDefault() {
        when(conflictRepository.findByTenantIdAndResolvedAtIsNullOrderByCreatedAtDesc(tenantId))
                .thenReturn(List.of(conflict()));

        assertThat(service.list(false)).hasSize(1);
    }

    private DomainEventEntity event() {
        DomainEventEntity event = new DomainEventEntity();
        event.id = UUID.randomUUID();
        event.tenantId = tenantId;
        event.farmId = farmId;
        event.aggregateType = "lot";
        event.aggregateId = aggregateId;
        event.eventType = "lot.headcount.updated";
        event.idempotencyKey = "device-1:key";
        event.payload = new LinkedHashMap<>(Map.of("headcount", 20, "expectedHeadcount", 20));
        return event;
    }

    private SyncConflictEntity conflict() {
        SyncConflictEntity conflict = new SyncConflictEntity();
        conflict.id = UUID.randomUUID();
        conflict.tenantId = tenantId;
        conflict.entityType = "lot";
        conflict.entityId = aggregateId;
        conflict.clientEventId = UUID.randomUUID();
        conflict.createdAt = Instant.parse("2026-11-10T08:15:00Z");
        return conflict;
    }
}
