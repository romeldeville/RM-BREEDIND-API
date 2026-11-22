package com.rmbreeding.api.identity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditEntryResponse(
            UUID id,
            UUID tenantId,
            UUID actorId,
            UUID supportSessionId,
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> before,
            Map<String, Object> after,
            String ip,
            String userAgent,
            Instant occurredAt) {
    }
}
