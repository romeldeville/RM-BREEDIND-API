package com.rmbreeding.api.sync;

import com.rmbreeding.api.identity.AuthContext;
import com.rmbreeding.api.identity.AuthPrincipal;
import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock
    private DomainEventRepository eventRepository;
    @Mock
    private SyncProjectionService projectionService;
    @Mock
    private SyncConflictService conflictService;
    @Mock
    private SyncClientService clientService;
    @Mock
    private AuditService auditService;

    private SyncService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID aggregateId = UUID.randomUUID();
    private final UUID farmId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SyncService(eventRepository, projectionService, conflictService, clientService,
                auditService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        AuthContext.clear();
    }

    // P13-02 / P13-04 - idempotent append

    @Test
    void acceptsNewEventAndAppliesProjection() {
        SyncDtos.PushEvent input = event("device-1:key-1");
        when(eventRepository.findByIdempotencyKey("device-1:key-1")).thenReturn(Optional.empty());
        when(eventRepository.existsById(input.id())).thenReturn(false);
        when(projectionService.find(tenantId, "lot", aggregateId)).thenReturn(Optional.empty());
        when(projectionService.detect(any(DomainEventEntity.class), isNull()))
                .thenReturn(Optional.empty());
        when(eventRepository.save(any(DomainEventEntity.class))).thenAnswer(invocation -> {
            DomainEventEntity entity = invocation.getArgument(0);
            entity.serverSeq = 10L;
            return entity;
        });
        when(eventRepository.maxServerSeq(tenantId)).thenReturn(10L);

        SyncDtos.PushResponse response = service.push(new SyncDtos.PushRequest("device-1", 0L,
                List.of(input)));

        assertThat(response.accepted()).containsExactly(input.id());
        assertThat(response.duplicates()).isEmpty();
        assertThat(response.rejected()).isEmpty();
        assertThat(response.serverSeq()).isEqualTo(10L);
        verify(projectionService).apply(any(DomainEventEntity.class), isNull());
        verify(clientService).touch(tenantId, "device-1", 10L);
    }

    @Test
    void ignoresDuplicateIdempotencyKeyWithAck() {
        SyncDtos.PushEvent input = event("device-1:key-1");
        DomainEventEntity existing = new DomainEventEntity();
        existing.id = input.id();
        existing.tenantId = tenantId;
        existing.idempotencyKey = input.idempotencyKey();
        when(eventRepository.findByIdempotencyKey("device-1:key-1")).thenReturn(Optional.of(existing));
        when(eventRepository.maxServerSeq(tenantId)).thenReturn(12L);

        SyncDtos.PushResponse response = service.push(new SyncDtos.PushRequest("device-1", 0L,
                List.of(input)));

        assertThat(response.duplicates()).containsExactly(input.id());
        assertThat(response.accepted()).isEmpty();
        verify(eventRepository, never()).save(any(DomainEventEntity.class));
        verify(projectionService, never()).apply(any(DomainEventEntity.class), any());
    }

    @Test
    void rejectsIdempotencyKeyFromAnotherTenant() {
        SyncDtos.PushEvent input = event("device-1:key-1");
        DomainEventEntity existing = new DomainEventEntity();
        existing.id = UUID.randomUUID();
        existing.tenantId = UUID.randomUUID();
        when(eventRepository.findByIdempotencyKey("device-1:key-1")).thenReturn(Optional.of(existing));
        when(eventRepository.maxServerSeq(tenantId)).thenReturn(12L);

        SyncDtos.PushResponse response = service.push(new SyncDtos.PushRequest("device-1", 0L,
                List.of(input)));

        assertThat(response.rejected()).hasSize(1);
        assertThat(response.rejected().get(0).reason())
                .isEqualTo("IDEMPOTENCY_KEY_OWNED_BY_ANOTHER_TENANT");
    }

    // P13-07 - divergent headcount becomes a conflict, no silent overwrite

    @Test
    void recordsConflictInsteadOfOverwritingDivergentHeadcount() {
        SyncDtos.PushEvent input = event("device-1:key-2");
        when(eventRepository.findByIdempotencyKey("device-1:key-2")).thenReturn(Optional.empty());
        when(eventRepository.existsById(input.id())).thenReturn(false);
        SyncProjectionEntity projection = new SyncProjectionEntity();
        projection.tenantId = tenantId;
        projection.aggregateType = "lot";
        projection.aggregateId = aggregateId;
        projection.eventId = UUID.randomUUID();
        when(projectionService.find(tenantId, "lot", aggregateId)).thenReturn(Optional.of(projection));
        SyncProjectionService.ConflictSignal signal =
                new SyncProjectionService.ConflictSignal("HEADCOUNT_MISMATCH", 20, 18);
        when(projectionService.detect(any(DomainEventEntity.class), eq(projection)))
                .thenReturn(Optional.of(signal));
        when(eventRepository.save(any(DomainEventEntity.class))).thenAnswer(invocation -> {
            DomainEventEntity entity = invocation.getArgument(0);
            entity.serverSeq = 20L;
            return entity;
        });
        SyncConflictEntity conflict = new SyncConflictEntity();
        conflict.id = UUID.randomUUID();
        conflict.tenantId = tenantId;
        conflict.entityType = "lot";
        conflict.entityId = aggregateId;
        when(conflictService.record(any(DomainEventEntity.class), eq(projection), eq(signal)))
                .thenReturn(conflict);
        when(eventRepository.maxServerSeq(tenantId)).thenReturn(20L);

        SyncDtos.PushResponse response = service.push(new SyncDtos.PushRequest("device-1", 0L,
                List.of(input)));

        assertThat(response.conflicts()).hasSize(1);
        assertThat(response.conflicts().get(0).entityId()).isEqualTo(aggregateId);
        verify(projectionService, never()).apply(any(DomainEventEntity.class), any());
    }

    // P16-02 - correction d'événement : 24 h et rôle

    @Test
    void acceptsCorrectionWithin24HoursForManager() {
        UUID targetId = UUID.randomUUID();
        DomainEventEntity target = storedEvent(7L);
        target.id = targetId;
        target.occurredAt = Instant.now().minusSeconds(3600);
        AuthContext.set(new AuthPrincipal(UUID.randomUUID(), tenantId, List.of("MANAGER"), false, null));
        SyncDtos.PushEvent input = correction("device-1:correction", targetId);
        when(eventRepository.findByIdAndTenantId(targetId, tenantId)).thenReturn(Optional.of(target));
        when(eventRepository.existsByCorrectsEventId(targetId)).thenReturn(false);
        when(eventRepository.findByIdempotencyKey("device-1:correction")).thenReturn(Optional.empty());
        when(eventRepository.existsById(input.id())).thenReturn(false);
        when(projectionService.find(tenantId, "lot", aggregateId)).thenReturn(Optional.empty());
        when(projectionService.detect(any(DomainEventEntity.class), isNull())).thenReturn(Optional.empty());
        when(eventRepository.save(any(DomainEventEntity.class))).thenAnswer(invocation -> {
            DomainEventEntity entity = invocation.getArgument(0);
            entity.serverSeq = 30L;
            return entity;
        });
        when(eventRepository.maxServerSeq(tenantId)).thenReturn(30L);

        SyncDtos.PushResponse response = service.push(new SyncDtos.PushRequest("device-1", 0L,
                List.of(input)));

        assertThat(response.accepted()).containsExactly(input.id());
        assertThat(response.rejected()).isEmpty();
    }

    @Test
    void rejectsCorrectionOlderThan24Hours() {
        UUID targetId = UUID.randomUUID();
        DomainEventEntity target = storedEvent(7L);
        target.id = targetId;
        target.occurredAt = Instant.now().minusSeconds(25 * 3600);
        AuthContext.set(new AuthPrincipal(UUID.randomUUID(), tenantId, List.of("OWNER"), false, null));
        when(eventRepository.findByIdAndTenantId(targetId, tenantId)).thenReturn(Optional.of(target));
        when(eventRepository.maxServerSeq(tenantId)).thenReturn(0L);

        SyncDtos.PushResponse response = service.push(new SyncDtos.PushRequest("device-1", 0L,
                List.of(correction("device-1:correction-old", targetId))));

        assertThat(response.rejected()).singleElement()
                .satisfies(rejected -> assertThat(rejected.reason()).isEqualTo("CORRECTION_WINDOW_EXPIRED"));
        verify(eventRepository, never()).save(any(DomainEventEntity.class));
    }

    @Test
    void rejectsCorrectionForWorkerRole() {
        UUID targetId = UUID.randomUUID();
        AuthContext.set(new AuthPrincipal(UUID.randomUUID(), tenantId, List.of("WORKER"), false, null));
        when(eventRepository.maxServerSeq(tenantId)).thenReturn(0L);

        SyncDtos.PushResponse response = service.push(new SyncDtos.PushRequest("device-1", 0L,
                List.of(correction("device-1:correction-worker", targetId))));

        assertThat(response.rejected()).singleElement()
                .satisfies(rejected -> assertThat(rejected.reason()).isEqualTo("CORRECTION_FORBIDDEN"));
        verify(eventRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void rejectsCorrectionOfUnknownOrAlreadyCorrectedEvent() {
        UUID missing = UUID.randomUUID();
        UUID corrected = UUID.randomUUID();
        DomainEventEntity target = storedEvent(7L);
        target.id = corrected;
        target.occurredAt = Instant.now().minusSeconds(60);
        AuthContext.set(new AuthPrincipal(UUID.randomUUID(), tenantId, List.of("OWNER"), false, null));
        when(eventRepository.findByIdAndTenantId(missing, tenantId)).thenReturn(Optional.empty());
        when(eventRepository.findByIdAndTenantId(corrected, tenantId)).thenReturn(Optional.of(target));
        when(eventRepository.existsByCorrectsEventId(corrected)).thenReturn(true);
        when(eventRepository.maxServerSeq(tenantId)).thenReturn(0L);

        SyncDtos.PushResponse response = service.push(new SyncDtos.PushRequest("device-1", 0L,
                List.of(correction("device-1:missing", missing),
                        correction("device-1:already", corrected))));

        assertThat(response.rejected()).extracting(SyncDtos.RejectedEvent::reason)
                .containsExactly("CORRECTION_TARGET_NOT_FOUND", "EVENT_ALREADY_CORRECTED");
    }

    // P13-05 - pull delta and snapshots

    @Test
    void pullsEventsAfterCursorAndSnapshots() {
        DomainEventEntity first = storedEvent(11L);
        DomainEventEntity second = storedEvent(12L);
        when(eventRepository.findByTenantIdAndServerSeqGreaterThanOrderByServerSeqAsc(
                eq(tenantId), eq(10L), any(Pageable.class))).thenReturn(List.of(first, second));
        when(eventRepository.maxServerSeq(tenantId)).thenReturn(12L);
        SyncProjectionEntity lot = new SyncProjectionEntity();
        lot.aggregateType = "lot";
        lot.aggregateId = aggregateId;
        lot.serverSeq = 12L;
        lot.eventType = "lot.updated";
        lot.occurredAt = Instant.parse("2026-11-10T08:00:00Z");
        lot.payload = Map.of("headcount", 20);
        when(projectionService.forType(tenantId, "lot")).thenReturn(List.of(lot));
        when(projectionService.forType(tenantId, "animal")).thenReturn(List.of());
        when(projectionService.forType(tenantId, "alert")).thenReturn(List.of());

        SyncDtos.PullResponse response = service.pull(10L, 200, "device-1");

        assertThat(response.events()).hasSize(2);
        assertThat(response.serverSeq()).isEqualTo(12L);
        assertThat(response.hasMore()).isFalse();
        assertThat(response.snapshots().lots()).hasSize(1);
        assertThat(response.snapshots().lots().get(0).payload()).containsEntry("headcount", 20);
        verify(clientService).touch(tenantId, "device-1", 12L);
    }

    @Test
    void reportsHasMoreWhenPageIsFull() {
        DomainEventEntity first = storedEvent(11L);
        DomainEventEntity second = storedEvent(12L);
        when(eventRepository.findByTenantIdAndServerSeqGreaterThanOrderByServerSeqAsc(
                eq(tenantId), eq(0L), any(Pageable.class))).thenReturn(List.of(first, second));
        when(projectionService.forType(any(), anyString())).thenReturn(List.of());

        SyncDtos.PullResponse response = service.pull(0L, 1, null);

        assertThat(response.events()).hasSize(1);
        assertThat(response.serverSeq()).isEqualTo(11L);
        assertThat(response.hasMore()).isTrue();
    }

    // P13-06 - status

    @Test
    void reportsCursorAndPendingWork() {
        SyncClientEntity client = new SyncClientEntity();
        client.lastCursor = 4L;
        client.lastSeenAt = Instant.parse("2026-11-10T08:00:00Z");
        when(eventRepository.maxServerSeq(tenantId)).thenReturn(12L);
        when(clientService.find(tenantId, "device-1")).thenReturn(Optional.of(client));
        when(eventRepository.countByTenantIdAndServerSeqGreaterThan(tenantId, 4L)).thenReturn(8L);
        when(conflictService.countOpen()).thenReturn(2L);

        SyncDtos.StatusResponse response = service.status("device-1");

        assertThat(response.serverSeq()).isEqualTo(12L);
        assertThat(response.clientCursor()).isEqualTo(4L);
        assertThat(response.pendingServerEvents()).isEqualTo(8L);
        assertThat(response.pendingConflicts()).isEqualTo(2L);
    }

    // P13-10 - projection rebuild

    @Test
    void rebuildsProjectionsAndReportsCounts() {
        when(projectionService.rebuild(tenantId)).thenReturn(3);
        when(eventRepository.maxServerSeq(tenantId)).thenReturn(12L);
        when(projectionService.count(tenantId)).thenReturn(2L);

        SyncDtos.RebuildResponse response = service.rebuildProjections();

        assertThat(response.rebuiltEvents()).isEqualTo(3);
        assertThat(response.projections()).isEqualTo(2);
        assertThat(response.serverSeq()).isEqualTo(12L);
        verify(auditService).record(eq("SYNC_PROJECTIONS_REBUILT"), eq("sync_projection"), isNull());
    }

    private SyncDtos.PushEvent event(String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("headcount", 20);
        return new SyncDtos.PushEvent(UUID.randomUUID(), "lot", aggregateId, "lot.headcount.updated",
                1, Instant.parse("2026-11-10T08:15:00Z"), idempotencyKey, payload, farmId,
                "device-1", null, Map.of());
    }

    private SyncDtos.PushEvent correction(String idempotencyKey, UUID correctsEventId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("headcount", 18);
        return new SyncDtos.PushEvent(UUID.randomUUID(), "lot", aggregateId, "lot.headcount.corrected",
                1, Instant.now(), idempotencyKey, payload, farmId, "device-1", correctsEventId,
                Map.of());
    }

    private DomainEventEntity storedEvent(long seq) {
        DomainEventEntity event = new DomainEventEntity();
        event.id = UUID.randomUUID();
        event.tenantId = tenantId;
        event.aggregateType = "lot";
        event.aggregateId = aggregateId;
        event.eventType = "lot.headcount.updated";
        event.eventVersion = 1;
        event.occurredAt = Instant.parse("2026-11-10T08:15:00Z");
        event.recordedAt = Instant.parse("2026-11-10T08:16:00Z");
        event.idempotencyKey = "device-1:" + seq;
        event.serverSeq = seq;
        event.payload = Map.of("headcount", 20);
        return event;
    }
}
