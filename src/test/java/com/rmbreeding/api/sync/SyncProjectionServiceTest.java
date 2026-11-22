package com.rmbreeding.api.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncProjectionServiceTest {

    @Mock
    private SyncProjectionRepository projectionRepository;
    @Mock
    private DomainEventRepository eventRepository;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID aggregateId = UUID.randomUUID();

    // P13-03 - write projections

    @Test
    void appliesEventPayloadToProjection() {
        SyncProjectionService service = new SyncProjectionService(projectionRepository,
                eventRepository);
        DomainEventEntity event = event("lot.headcount.updated", 10L, 20,
                Instant.parse("2026-11-10T08:00:00Z"));
        when(projectionRepository.findByTenantIdAndAggregateTypeAndAggregateId(
                tenantId, "lot", aggregateId)).thenReturn(Optional.empty());

        service.apply(event);

        ArgumentCaptor<SyncProjectionEntity> captor =
                ArgumentCaptor.forClass(SyncProjectionEntity.class);
        verify(projectionRepository).save(captor.capture());
        SyncProjectionEntity saved = captor.getValue();
        assertThat(saved.aggregateType).isEqualTo("lot");
        assertThat(saved.eventType).isEqualTo("lot.headcount.updated");
        assertThat(saved.serverSeq).isEqualTo(10L);
        assertThat(saved.payload).containsEntry("headcount", 20);
    }

    @Test
    void detectsDivergentHeadcount() {
        SyncProjectionService service = new SyncProjectionService(projectionRepository,
                eventRepository);
        DomainEventEntity event = event("lot.headcount.updated", 10L, 20,
                Instant.parse("2026-11-10T08:00:00Z"));
        event.payload.put("expectedHeadcount", 18);
        SyncProjectionEntity projection = projection(19);

        Optional<SyncProjectionService.ConflictSignal> signal = service.detect(event, projection);

        assertThat(signal).isPresent();
        assertThat(signal.get().reason()).isEqualTo("HEADCOUNT_MISMATCH");
        assertThat(signal.get().expected()).isEqualTo(18);
        assertThat(signal.get().actual()).isEqualTo(19);
    }

    @Test
    void acceptsMatchingHeadcount() {
        SyncProjectionService service = new SyncProjectionService(projectionRepository,
                eventRepository);
        DomainEventEntity event = event("lot.headcount.updated", 10L, 20,
                Instant.parse("2026-11-10T08:00:00Z"));
        event.payload.put("expectedHeadcount", 19);

        assertThat(service.detect(event, projection(19))).isEmpty();
    }

    @Test
    void detectsDivergentStock() {
        SyncProjectionService service = new SyncProjectionService(projectionRepository,
                eventRepository);
        DomainEventEntity event = event("feed.stock.updated", 10L, 100,
                Instant.parse("2026-11-10T08:00:00Z"));
        event.aggregateType = "feed_stock";
        event.payload.put("expectedStock", 80);
        SyncProjectionEntity projection = projection(19);
        projection.aggregateType = "feed_stock";
        projection.payload = new LinkedHashMap<>(Map.of("stockQuantity", 75));

        Optional<SyncProjectionService.ConflictSignal> signal = service.detect(event, projection);

        assertThat(signal).isPresent();
        assertThat(signal.get().reason()).isEqualTo("STOCK_MISMATCH");
    }

    @Test
    void replaysAggregateForLateEvent() {
        SyncProjectionService service = new SyncProjectionService(projectionRepository,
                eventRepository);
        Instant late = Instant.parse("2026-11-09T08:00:00Z");
        DomainEventEntity lateEvent = event("lot.mortality.recorded", 12L, 18, late);
        lateEvent.payload.put("cause", "ecrasement");
        DomainEventEntity first = event("lot.headcount.updated", 10L, 20,
                Instant.parse("2026-11-08T08:00:00Z"));
        when(eventRepository
                .findByTenantIdAndAggregateTypeAndAggregateIdOrderByOccurredAtAscServerSeqAsc(
                        tenantId, "lot", aggregateId)).thenReturn(List.of(first, lateEvent));
        SyncProjectionEntity existing = projection(20);
        existing.occurredAt = Instant.parse("2026-11-10T08:00:00Z");

        service.apply(lateEvent, existing);

        ArgumentCaptor<SyncProjectionEntity> captor =
                ArgumentCaptor.forClass(SyncProjectionEntity.class);
        verify(projectionRepository).save(captor.capture());
        assertThat(captor.getValue().payload).containsEntry("cause", "ecrasement");
        assertThat(captor.getValue().payload).containsEntry("headcount", 18);
    }

    // P13-10 - rebuild

    @Test
    void rebuildsOneProjectionPerAggregate() {
        SyncProjectionService service = new SyncProjectionService(projectionRepository,
                eventRepository);
        DomainEventEntity first = event("lot.created", 10L, 20,
                Instant.parse("2026-11-08T08:00:00Z"));
        DomainEventEntity second = event("lot.headcount.updated", 11L, 18,
                Instant.parse("2026-11-10T08:00:00Z"));
        when(eventRepository.findByTenantIdOrderByOccurredAtAscServerSeqAsc(tenantId))
                .thenReturn(List.of(first, second));

        int rebuilt = service.rebuild(tenantId);

        assertThat(rebuilt).isEqualTo(2);
        verify(projectionRepository).deleteAllForTenant(tenantId);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<SyncProjectionEntity>> captor =
                ArgumentCaptor.forClass(Collection.class);
        verify(projectionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        SyncProjectionEntity projection = captor.getValue().iterator().next();
        assertThat(projection.payload).containsEntry("headcount", 18);
        assertThat(projection.serverSeq).isEqualTo(11L);
    }

    @Test
    void appliesClientWinningResolution() {
        SyncProjectionService service = new SyncProjectionService(projectionRepository,
                eventRepository);
        SyncConflictEntity conflict = new SyncConflictEntity();
        conflict.id = UUID.randomUUID();
        conflict.tenantId = tenantId;
        conflict.entityType = "lot";
        conflict.entityId = aggregateId;
        conflict.clientEventId = UUID.randomUUID();
        conflict.payload = Map.of("incoming", Map.of("headcount", 21));
        when(projectionRepository.findByTenantIdAndAggregateTypeAndAggregateId(
                tenantId, "lot", aggregateId)).thenReturn(Optional.empty());

        service.applyResolution(conflict, null);

        ArgumentCaptor<SyncProjectionEntity> captor =
                ArgumentCaptor.forClass(SyncProjectionEntity.class);
        verify(projectionRepository).save(captor.capture());
        assertThat(captor.getValue().payload).containsEntry("headcount", 21);
        assertThat(captor.getValue().eventType).isEqualTo("conflict.resolved");
    }

    private DomainEventEntity event(String type, long seq, int count, Instant occurredAt) {
        DomainEventEntity event = new DomainEventEntity();
        event.id = UUID.randomUUID();
        event.tenantId = tenantId;
        event.aggregateType = "lot";
        event.aggregateId = aggregateId;
        event.eventType = type;
        event.eventVersion = 1;
        event.occurredAt = occurredAt;
        event.serverSeq = seq;
        event.payload = new LinkedHashMap<>(Map.of("headcount", count));
        return event;
    }

    private SyncProjectionEntity projection(int headcount) {
        SyncProjectionEntity projection = new SyncProjectionEntity();
        projection.id = UUID.randomUUID();
        projection.tenantId = tenantId;
        projection.aggregateType = "lot";
        projection.aggregateId = aggregateId;
        projection.eventId = UUID.randomUUID();
        projection.serverSeq = 9L;
        projection.eventType = "lot.headcount.updated";
        projection.occurredAt = Instant.parse("2026-11-09T08:00:00Z");
        projection.payload = new LinkedHashMap<>(Map.of("headcount", headcount));
        return projection;
    }
}
