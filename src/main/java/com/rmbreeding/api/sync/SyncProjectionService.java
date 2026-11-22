package com.rmbreeding.api.sync;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Maintains the write-side projections fed by domain events.
 *
 * <p>A projection stores the latest known payload of an aggregate. Events are applied in
 * occurrence order; a late event triggers a replay of its aggregate so the real event date is
 * preserved without losing the other changes.</p>
 */
@Service
public class SyncProjectionService {

    private final SyncProjectionRepository projectionRepository;
    private final DomainEventRepository eventRepository;

    public SyncProjectionService(SyncProjectionRepository projectionRepository,
            DomainEventRepository eventRepository) {
        this.projectionRepository = projectionRepository;
        this.eventRepository = eventRepository;
    }

    public Optional<SyncProjectionEntity> find(UUID tenantId, String aggregateType, UUID aggregateId) {
        return projectionRepository.findByTenantIdAndAggregateTypeAndAggregateId(
                tenantId, aggregateType, aggregateId);
    }

    public List<SyncProjectionEntity> forType(UUID tenantId, String aggregateType) {
        return projectionRepository.findByTenantIdAndAggregateTypeOrderByUpdatedAtDesc(tenantId,
                aggregateType);
    }

    public long count(UUID tenantId) {
        return projectionRepository.countByTenantId(tenantId);
    }

    /**
     * Detect divergent headcount or stock signals before applying the event.
     */
    public Optional<ConflictSignal> detect(DomainEventEntity event, SyncProjectionEntity projection) {
        if (projection == null || projection.payload == null || event.payload == null) {
            return Optional.empty();
        }
        Optional<ConflictSignal> headcount = mismatch(event.payload, projection.payload,
                "expectedHeadcount", "headcount", "HEADCOUNT_MISMATCH");
        if (headcount.isPresent()) {
            return headcount;
        }
        return mismatch(event.payload, projection.payload,
                "expectedStock", "stockQuantity", "STOCK_MISMATCH");
    }

    @Transactional
    public void apply(DomainEventEntity event) {
        SyncProjectionEntity existing = projectionRepository
                .findByTenantIdAndAggregateTypeAndAggregateId(event.tenantId, event.aggregateType,
                        event.aggregateId)
                .orElse(null);
        apply(event, existing);
    }

    @Transactional
    public void apply(DomainEventEntity event, SyncProjectionEntity existing) {
        if (existing != null && existing.occurredAt != null && event.occurredAt != null
                && event.occurredAt.isBefore(existing.occurredAt)) {
            replayAggregate(event);
            return;
        }
        mergeAndSave(event, existing);
    }

    /**
     * Recompute every projection of the tenant from the full event journal.
     */
    @Transactional
    public int rebuild(UUID tenantId) {
        projectionRepository.deleteAllForTenant(tenantId);
        List<DomainEventEntity> events = eventRepository
                .findByTenantIdOrderByOccurredAtAscServerSeqAsc(tenantId);
        Map<String, SyncProjectionEntity> projections = new LinkedHashMap<>();
        for (DomainEventEntity event : events) {
            String key = event.aggregateType + ":" + event.aggregateId;
            SyncProjectionEntity projection = projections.get(key);
            if (projection == null) {
                projection = newProjection(event);
                projections.put(key, projection);
            }
            merge(projection, event);
        }
        projectionRepository.saveAll(projections.values());
        return events.size();
    }

    /**
     * Apply a conflict resolution that chose the client value (or a merged value).
     */
    @Transactional
    public void applyResolution(SyncConflictEntity conflict, Map<String, Object> overridePayload) {
        SyncProjectionEntity projection = projectionRepository
                .findByTenantIdAndAggregateTypeAndAggregateId(conflict.tenantId, conflict.entityType,
                        conflict.entityId)
                .orElseGet(() -> {
                    SyncProjectionEntity created = new SyncProjectionEntity();
                    created.id = UUID.randomUUID();
                    created.tenantId = conflict.tenantId;
                    created.aggregateType = conflict.entityType;
                    created.aggregateId = conflict.entityId;
                    created.serverSeq = 0;
                    created.payload = new LinkedHashMap<>();
                    return created;
                });
        Map<String, Object> incoming = overridePayload != null
                ? overridePayload
                : asMap(conflict.payload == null ? null : conflict.payload.get("incoming"));
        Map<String, Object> merged = new LinkedHashMap<>();
        if (projection.payload != null) {
            merged.putAll(projection.payload);
        }
        merged.putAll(incoming);
        projection.payload = merged;
        projection.eventId = conflict.clientEventId;
        projection.eventType = "conflict.resolved";
        projection.serverSeq = Math.max(projection.serverSeq, 0);
        projection.occurredAt = Instant.now();
        projection.updatedAt = Instant.now();
        projectionRepository.save(projection);
    }

    private void replayAggregate(DomainEventEntity event) {
        List<DomainEventEntity> history = eventRepository
                .findByTenantIdAndAggregateTypeAndAggregateIdOrderByOccurredAtAscServerSeqAsc(
                        event.tenantId, event.aggregateType, event.aggregateId);
        if (history.isEmpty()) {
            mergeAndSave(event, null);
            return;
        }
        SyncProjectionEntity projection = newProjection(event);
        projection.payload = new LinkedHashMap<>();
        for (DomainEventEntity item : history) {
            merge(projection, item);
        }
        projectionRepository.save(projection);
    }

    private void mergeAndSave(DomainEventEntity event, SyncProjectionEntity existing) {
        SyncProjectionEntity projection = existing != null ? existing : newProjection(event);
        merge(projection, event);
        projectionRepository.save(projection);
    }

    private void merge(SyncProjectionEntity projection, DomainEventEntity event) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (projection.payload != null) {
            merged.putAll(projection.payload);
        }
        if (event.payload != null) {
            merged.putAll(event.payload);
        }
        projection.payload = merged;
        projection.eventId = event.id;
        projection.eventType = event.eventType;
        projection.serverSeq = event.serverSeq == null ? Math.max(projection.serverSeq, 0) : event.serverSeq;
        projection.occurredAt = event.occurredAt;
        projection.updatedAt = Instant.now();
    }

    private SyncProjectionEntity newProjection(DomainEventEntity event) {
        SyncProjectionEntity projection = new SyncProjectionEntity();
        projection.id = UUID.randomUUID();
        projection.tenantId = event.tenantId;
        projection.aggregateType = event.aggregateType;
        projection.aggregateId = event.aggregateId;
        projection.serverSeq = event.serverSeq == null ? 0 : event.serverSeq;
        projection.payload = new LinkedHashMap<>();
        return projection;
    }

    private Optional<ConflictSignal> mismatch(Map<String, Object> eventPayload,
            Map<String, Object> projectionPayload, String expectedField, String actualField,
            String reason) {
        Object expected = eventPayload.get(expectedField);
        Object actual = projectionPayload.get(actualField);
        if (expected == null || actual == null) {
            return Optional.empty();
        }
        if (sameNumber(expected, actual)) {
            return Optional.empty();
        }
        return Optional.of(new ConflictSignal(reason, expected, actual));
    }

    private static boolean sameNumber(Object expected, Object actual) {
        try {
            return new BigDecimal(expected.toString()).compareTo(new BigDecimal(actual.toString())) == 0;
        } catch (NumberFormatException ex) {
            return expected.equals(actual);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    public record ConflictSignal(String reason, Object expected, Object actual) {
    }
}
