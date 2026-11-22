package com.rmbreeding.api.sync;

import com.rmbreeding.api.alerts.AlertsService;
import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SyncConflictService {

    static final Set<String> RESOLUTIONS = Set.of("CLIENT_WINS", "SERVER_WINS", "MERGED");

    private final SyncConflictRepository conflictRepository;
    private final SyncProjectionService projectionService;
    private final AlertsService alertsService;
    private final AuditService auditService;

    public SyncConflictService(SyncConflictRepository conflictRepository,
            SyncProjectionService projectionService,
            AlertsService alertsService,
            AuditService auditService) {
        this.conflictRepository = conflictRepository;
        this.projectionService = projectionService;
        this.alertsService = alertsService;
        this.auditService = auditService;
    }

    @Transactional
    public SyncConflictEntity record(DomainEventEntity event, SyncProjectionEntity projection,
            SyncProjectionService.ConflictSignal signal) {
        SyncConflictEntity conflict = new SyncConflictEntity();
        conflict.id = UUID.randomUUID();
        conflict.tenantId = event.tenantId;
        conflict.entityType = event.aggregateType;
        conflict.entityId = event.aggregateId;
        conflict.clientEventId = event.id;
        conflict.serverEventId = projection == null ? null : projection.eventId;
        conflict.createdAt = Instant.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", signal.reason());
        payload.put("expected", signal.expected());
        payload.put("actual", signal.actual());
        payload.put("eventType", event.eventType);
        payload.put("incoming", event.payload == null ? Map.of() : event.payload);
        conflict.payload = payload;
        conflictRepository.save(conflict);

        if (event.farmId != null) {
            alertsService.raise(event.tenantId, event.farmId, "SYNC_CONFLICT", "CRITICAL",
                    "Conflit de synchronisation",
                    "Valeur divergente (" + signal.reason() + ") pour " + event.aggregateType
                            + " " + event.aggregateId,
                    LocalDate.now(), "sync_conflict", conflict.id);
        }
        auditService.record("SYNC_CONFLICT_DETECTED", "sync_conflict", conflict.id);
        return conflict;
    }

    public List<SyncConflictEntity> list(boolean includeResolved) {
        UUID tenantId = TenantContext.requireTenantId();
        return includeResolved
                ? conflictRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : conflictRepository.findByTenantIdAndResolvedAtIsNullOrderByCreatedAtDesc(tenantId);
    }

    public long countOpen() {
        return conflictRepository.countByTenantIdAndResolvedAtIsNull(
                TenantContext.requireTenantId());
    }

    @Transactional
    public SyncConflictEntity resolve(UUID conflictId, SyncDtos.ResolveConflictRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        SyncConflictEntity conflict = conflictRepository.findByIdAndTenantId(conflictId, tenantId)
                .orElseThrow(() -> new NotFoundException("Conflict not found"));
        if (conflict.resolvedAt != null) {
            throw new ConflictException("Conflict is already resolved");
        }
        String resolution = request.resolution().trim().toUpperCase(Locale.ROOT);
        if (!RESOLUTIONS.contains(resolution)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Unsupported resolution: " + request.resolution());
        }
        if (!"SERVER_WINS".equals(resolution)) {
            projectionService.applyResolution(conflict, request.payload());
        }
        conflict.resolution = resolution;
        conflict.resolvedAt = Instant.now();
        conflictRepository.save(conflict);
        alertsService.autoClose(tenantId, "sync_conflict", conflict.id);
        auditService.record("SYNC_CONFLICT_RESOLVED", "sync_conflict", conflict.id);
        return conflict;
    }
}
