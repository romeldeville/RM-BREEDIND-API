package com.rmbreeding.api.notifications;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notification_log")
class NotificationLogEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID userId;

    String channel;

    String type;

    @JdbcTypeCode(SqlTypes.JSON)
    Map<String, Object> payload;

    String status;

    Instant sentAt;

    String error;
}
