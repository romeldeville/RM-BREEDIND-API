package com.rmbreeding.api.alerts;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published whenever a new alert is persisted. Notifications listen to this
 * event through the public module boundary.
 */
public record AlertRaisedEvent(
        UUID tenantId,
        UUID farmId,
        String type,
        String priority,
        String title,
        String message,
        LocalDate dueDate,
        String entityType,
        UUID entityId) {
}
