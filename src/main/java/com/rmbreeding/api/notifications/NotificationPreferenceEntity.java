package com.rmbreeding.api.notifications;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
class NotificationPreferenceEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID userId;

    String type;

    String channel;

    boolean enabled;
}
