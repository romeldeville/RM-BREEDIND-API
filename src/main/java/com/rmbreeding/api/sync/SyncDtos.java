package com.rmbreeding.api.sync;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SyncDtos {

    private SyncDtos() {
    }

    public record PushRequest(
            @NotBlank @Size(max = 80) String deviceId,
            Long clientCursor,
            @NotEmpty @Size(max = 200) List<@Valid PushEvent> events) {
    }

    public record PushEvent(
            @NotNull UUID id,
            @NotBlank @Size(max = 60) String aggregateType,
            @NotNull UUID aggregateId,
            @NotBlank @Size(max = 80) String eventType,
            Integer eventVersion,
            @NotNull Instant occurredAt,
            @NotBlank @Size(max = 200) String idempotencyKey,
            Map<String, Object> payload,
            UUID farmId,
            String clientId,
            UUID correctsEventId,
            Map<String, Object> metadata) {
    }

    public record PushResponse(
            List<UUID> accepted,
            List<UUID> duplicates,
            List<RejectedEvent> rejected,
            List<ConflictView> conflicts,
            long serverSeq) {
    }

    public record RejectedEvent(UUID id, String reason) {
    }

    public record PullResponse(
            long serverSeq,
            List<EventView> events,
            Snapshots snapshots,
            boolean hasMore) {
    }

    public record EventView(
            UUID id,
            UUID farmId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            int eventVersion,
            Instant occurredAt,
            Instant recordedAt,
            String idempotencyKey,
            long serverSeq,
            Map<String, Object> payload,
            UUID correctsEventId,
            Map<String, Object> metadata) {
    }

    public record Snapshots(
            List<SnapshotItem> lots,
            List<SnapshotItem> animals,
            List<SnapshotItem> alerts) {
    }

    public record SnapshotItem(
            String aggregateType,
            UUID aggregateId,
            long serverSeq,
            String eventType,
            Instant occurredAt,
            Map<String, Object> payload) {
    }

    public record StatusResponse(
            String deviceId,
            long serverSeq,
            long clientCursor,
            long pendingServerEvents,
            long pendingConflicts,
            Instant lastSeenAt) {
    }

    public record ConflictView(
            UUID id,
            String entityType,
            UUID entityId,
            UUID clientEventId,
            UUID serverEventId,
            String resolution,
            Instant resolvedAt,
            Instant createdAt,
            Map<String, Object> payload) {
    }

    public record ResolveConflictRequest(
            @NotBlank @Size(max = 40) String resolution,
            Map<String, Object> payload) {
    }

    public record RebuildResponse(
            int rebuiltEvents,
            long serverSeq,
            long projections) {
    }
}
