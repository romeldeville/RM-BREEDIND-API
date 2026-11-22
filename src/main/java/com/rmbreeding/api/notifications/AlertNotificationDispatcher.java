package com.rmbreeding.api.notifications;

import com.rmbreeding.api.alerts.AlertRaisedEvent;
import com.rmbreeding.api.shared.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * P15-03 - sends a push notification to every registered device of the tenant
 * when an important or critical alert is raised.
 */
@Service
public class AlertNotificationDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(AlertNotificationDispatcher.class);
    private static final Set<String> PUSH_PRIORITIES = Set.of("IMPORTANT", "CRITICAL");

    private final PushTokenRepository pushTokenRepository;
    private final NotificationService notificationService;
    private final PushSender pushSender;

    public AlertNotificationDispatcher(PushTokenRepository pushTokenRepository,
            NotificationService notificationService,
            PushSender pushSender) {
        this.pushTokenRepository = pushTokenRepository;
        this.notificationService = notificationService;
        this.pushSender = pushSender;
    }

    public int dispatch(AlertRaisedEvent event) {
        if (!PUSH_PRIORITIES.contains(event.priority())) {
            return 0;
        }
        UUID previousTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(event.tenantId());
        try {
            List<PushTokenEntity> tokens = pushTokenRepository.findByTenantId(event.tenantId());
            Map<String, String> data = data(event);
            int sent = 0;
            for (PushTokenEntity token : tokens) {
                if (!notificationService.isEnabled(event.tenantId(), token.userId, event.type(),
                        NotificationTypes.CHANNEL_PUSH)) {
                    continue;
                }
                try {
                    pushSender.send(token.token, event.title(), event.message(), data);
                    notificationService.log(event.tenantId(), token.userId,
                            NotificationTypes.CHANNEL_PUSH, event.type(), new LinkedHashMap<>(data),
                            "SENT", null);
                    sent++;
                } catch (RuntimeException ex) {
                    LOG.warn("Push notification failed for tenant {}: {}", event.tenantId(), ex.getMessage());
                    notificationService.log(event.tenantId(), token.userId,
                            NotificationTypes.CHANNEL_PUSH, event.type(), new LinkedHashMap<>(data),
                            "FAILED", ex.getMessage());
                }
            }
            return sent;
        } finally {
            restore(previousTenant);
        }
    }

    private static Map<String, String> data(AlertRaisedEvent event) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("alertType", event.type());
        data.put("priority", event.priority());
        data.put("title", event.title());
        if (event.entityType() != null) {
            data.put("entityType", event.entityType());
        }
        if (event.entityId() != null) {
            data.put("entityId", event.entityId().toString());
        }
        if (event.dueDate() != null) {
            data.put("dueDate", event.dueDate().toString());
        }
        if (event.farmId() != null) {
            data.put("farmId", event.farmId().toString());
        }
        return data;
    }

    private static void restore(UUID previousTenant) {
        if (previousTenant == null) {
            TenantContext.clear();
        } else {
            TenantContext.setTenantId(previousTenant);
        }
    }
}
