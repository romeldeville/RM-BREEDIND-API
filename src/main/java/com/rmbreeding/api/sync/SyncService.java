package com.rmbreeding.api.sync;

import com.rmbreeding.api.identity.AuthContext;
import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SyncService {

    private static final int DEFAULT_PULL_LIMIT = 200;
    private static final int MAX_PULL_LIMIT = 500;

    private final DomainEventRepository eventRepository;
    private final SyncProjectionService projectionService;
    private final SyncConflictService conflictService;
    private final SyncClientService clientService;
    private final AuditService auditService;

    public SyncService(DomainEventRepository eventRepository,
            SyncProjectionService projectionService,
            SyncConflictService conflictService,
            SyncClientService clientService,
            AuditService auditService) {
        this.eventRepository = eventRepository;
        this.projectionService = projectionService;
        this.conflictService = conflictService;
        this.clientService = clientService;
        this.auditService = auditService;
    }

    // P13-02 / P13-04 / P13-07 / P13-09

    @Transactional
    public SyncDtos.PushResponse push(SyncDtos.PushRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        Instant now = Instant.now();
        List<UUID> accepted = new ArrayList<>();
        List<UUID> duplicates = new ArrayList<>();
        List<SyncDtos.RejectedEvent> rejected = new ArrayList<>();
        List<SyncDtos.ConflictView> conflicts = new ArrayList<>();

        for (SyncDtos.PushEvent input : request.events()) {
            String rejection = validate(input, now);
            if (rejection == null) {
                rejection = validateCorrection(input, now);
            }
            if (rejection != null) {
                rejected.add(new SyncDtos.RejectedEvent(input.id(), rejection));
                continue;
            }
            Optional<DomainEventEntity> known = eventRepository
                    .findByIdempotencyKey(input.idempotencyKey());
            if (known.isPresent()) {
                if (tenantId.equals(known.get().tenantId)) {
                    duplicates.add(input.id());
                } else {
                    rejected.add(new SyncDtos.RejectedEvent(input.id(),
                            "IDEMPOTENCY_KEY_OWNED_BY_ANOTHER_TENANT"));
                }
                continue;
            }
            if (eventRepository.existsById(input.id())) {
                duplicates.add(input.id());
                continue;
            }

            DomainEventEntity event = toEntity(tenantId, input, now);
            SyncProjectionEntity existing = projectionService
                    .find(tenantId, event.aggregateType, event.aggregateId)
                    .orElse(null);
            Optional<SyncProjectionService.ConflictSignal> signal = projectionService
                    .detect(event, existing);

            DomainEventEntity saved = eventRepository.save(event);
            eventRepository.flush();
            accepted.add(saved.id);
            if (signal.isPresent()) {
                SyncConflictEntity conflict = conflictService.record(saved, existing, signal.get());
                conflicts.add(toView(conflict));
            } else {
                projectionService.apply(saved, existing);
            }
        }

        long serverSeq = eventRepository.maxServerSeq(tenantId);
        long cursor = request.clientCursor() == null ? 0 : request.clientCursor();
        clientService.touch(tenantId, request.deviceId(), Math.max(cursor, serverSeq));

        return new SyncDtos.PushResponse(accepted, duplicates, rejected, conflicts, serverSeq);
    }

    // P13-05

    @Transactional
    public SyncDtos.PullResponse pull(long since, int limit, String deviceId) {
        UUID tenantId = TenantContext.requireTenantId();
        int size = limit <= 0 ? DEFAULT_PULL_LIMIT : Math.min(limit, MAX_PULL_LIMIT);
        long from = Math.max(since, 0);

        List<DomainEventEntity> page = eventRepository
                .findByTenantIdAndServerSeqGreaterThanOrderByServerSeqAsc(tenantId, from,
                        PageRequest.of(0, size + 1));
        boolean hasMore = page.size() > size;
        List<DomainEventEntity> selected = hasMore ? page.subList(0, size) : page;
        List<SyncDtos.EventView> events = selected.stream().map(this::toView).toList();

        long maxSeq = eventRepository.maxServerSeq(tenantId);
        long serverSeq;
        if (hasMore && !events.isEmpty()) {
            serverSeq = events.get(events.size() - 1).serverSeq();
        } else {
            serverSeq = maxSeq;
        }

        if (deviceId != null && !deviceId.isBlank()) {
            clientService.touch(tenantId, deviceId, serverSeq);
        }
        return new SyncDtos.PullResponse(serverSeq, events, snapshots(tenantId), hasMore);
    }

    // P13-06

    @Transactional(readOnly = true)
    public SyncDtos.StatusResponse status(String deviceId) {
        UUID tenantId = TenantContext.requireTenantId();
        long maxSeq = eventRepository.maxServerSeq(tenantId);
        Optional<SyncClientEntity> client = clientService.find(tenantId, deviceId);
        long cursor = client.map(value -> value.lastCursor).orElse(0L);
        long pending = eventRepository.countByTenantIdAndServerSeqGreaterThan(tenantId, cursor);
        long conflicts = conflictService.countOpen();
        return new SyncDtos.StatusResponse(deviceId, maxSeq, cursor, pending, conflicts,
                client.map(value -> value.lastSeenAt).orElse(null));
    }

    // P13-08

    @Transactional
    public SyncDtos.ConflictView resolve(UUID conflictId, SyncDtos.ResolveConflictRequest request) {
        return toView(conflictService.resolve(conflictId, request));
    }

    public List<SyncDtos.ConflictView> conflicts(boolean includeResolved) {
        return conflictService.list(includeResolved).stream().map(this::toView).toList();
    }

    // P13-10

    @Transactional
    public SyncDtos.RebuildResponse rebuildProjections() {
        UUID tenantId = TenantContext.requireTenantId();
        int rebuilt = projectionService.rebuild(tenantId);
        long serverSeq = eventRepository.maxServerSeq(tenantId);
        long projections = projectionService.count(tenantId);
        auditService.record("SYNC_PROJECTIONS_REBUILT", "sync_projection", null);
        return new SyncDtos.RebuildResponse(rebuilt, serverSeq, projections);
    }

    private SyncDtos.Snapshots snapshots(UUID tenantId) {
        return new SyncDtos.Snapshots(
                snapshots(tenantId, "lot"),
                snapshots(tenantId, "animal"),
                snapshots(tenantId, "alert"));
    }

    private List<SyncDtos.SnapshotItem> snapshots(UUID tenantId, String aggregateType) {
        return projectionService.forType(tenantId, aggregateType).stream()
                .map(value -> new SyncDtos.SnapshotItem(value.aggregateType, value.aggregateId,
                        value.serverSeq, value.eventType, value.occurredAt,
                        value.payload == null ? Map.of() : value.payload))
                .toList();
    }

    private String validate(SyncDtos.PushEvent input, Instant now) {
        if (input.eventVersion() != null && input.eventVersion() < 1) {
            return "INVALID_EVENT_VERSION";
        }
        if (input.occurredAt().isAfter(now.plusSeconds(300))) {
            return "EVENT_DATE_IN_FUTURE";
        }
        if (input.aggregateType().isBlank() || input.eventType().isBlank()) {
            return "INVALID_EVENT_TYPE";
        }
        return null;
    }

    /**
     * P16-02 - une correction ne réécrit jamais l'événement d'origine. Elle est acceptée
     * uniquement dans les 24 h, par un rôle OWNER/MANAGER (ou platform admin), et une seule
     * fois par événement cible.
     */
    private String validateCorrection(SyncDtos.PushEvent input, Instant now) {
        if (input.correctsEventId() == null) {
            return null;
        }
        com.rmbreeding.api.identity.AuthPrincipal principal = AuthContext.get();
        boolean allowed = principal != null && (principal.platformAdmin()
                || principal.hasRole("OWNER") || principal.hasRole("MANAGER"));
        if (!allowed) {
            return "CORRECTION_FORBIDDEN";
        }
        UUID tenantId = TenantContext.requireTenantId();
        Optional<DomainEventEntity> target = eventRepository
                .findByIdAndTenantId(input.correctsEventId(), tenantId);
        if (target.isEmpty()) {
            return "CORRECTION_TARGET_NOT_FOUND";
        }
        if (target.get().occurredAt.isBefore(now.minus(Duration.ofHours(24)))) {
            return "CORRECTION_WINDOW_EXPIRED";
        }
        if (eventRepository.existsByCorrectsEventId(input.correctsEventId())) {
            return "EVENT_ALREADY_CORRECTED";
        }
        return null;
    }

    private DomainEventEntity toEntity(UUID tenantId, SyncDtos.PushEvent input, Instant now) {
        DomainEventEntity event = new DomainEventEntity();
        event.id = input.id();
        event.tenantId = tenantId;
        event.farmId = input.farmId();
        event.aggregateType = input.aggregateType().trim();
        event.aggregateId = input.aggregateId();
        event.eventType = input.eventType().trim();
        event.eventVersion = input.eventVersion() == null ? 1 : input.eventVersion();
        event.occurredAt = input.occurredAt();
        event.recordedAt = now;
        event.actorId = AuthContext.userId();
        event.clientId = input.clientId();
        event.idempotencyKey = input.idempotencyKey();
        event.payload = copy(input.payload());
        event.correctsEventId = input.correctsEventId();
        event.metadata = copy(input.metadata());
        return event;
    }

    private static Map<String, Object> copy(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    private SyncDtos.EventView toView(DomainEventEntity event) {
        return new SyncDtos.EventView(event.id, event.farmId, event.aggregateType,
                event.aggregateId, event.eventType, event.eventVersion, event.occurredAt,
                event.recordedAt, event.idempotencyKey,
                event.serverSeq == null ? 0 : event.serverSeq,
                event.payload == null ? Map.of() : event.payload,
                event.correctsEventId,
                event.metadata == null ? Map.of() : event.metadata);
    }

    private SyncDtos.ConflictView toView(SyncConflictEntity conflict) {
        return new SyncDtos.ConflictView(conflict.id, conflict.entityType, conflict.entityId,
                conflict.clientEventId, conflict.serverEventId, conflict.resolution,
                conflict.resolvedAt, conflict.createdAt,
                conflict.payload == null ? Map.of() : conflict.payload);
    }
}
