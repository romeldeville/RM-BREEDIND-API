package com.rmbreeding.api.notifications;

import com.rmbreeding.api.identity.AuthContext;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private static final int MAX_ERROR_LENGTH = 500;

    private final PushTokenRepository pushTokenRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationLogRepository logRepository;

    public NotificationService(PushTokenRepository pushTokenRepository,
            NotificationPreferenceRepository preferenceRepository,
            NotificationLogRepository logRepository) {
        this.pushTokenRepository = pushTokenRepository;
        this.preferenceRepository = preferenceRepository;
        this.logRepository = logRepository;
    }

    // P15-02 - device token registration

    @Transactional
    public NotificationDtos.TokenResponse registerToken(NotificationDtos.TokenRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = requireUserId();
        Instant now = Instant.now();
        PushTokenEntity entity = pushTokenRepository.findByToken(request.token())
                .orElseGet(() -> {
                    PushTokenEntity created = new PushTokenEntity();
                    created.id = UUID.randomUUID();
                    created.token = request.token();
                    created.createdAt = now;
                    return created;
                });
        entity.tenantId = tenantId;
        entity.userId = userId;
        entity.platform = request.platform().toUpperCase(java.util.Locale.ROOT);
        entity.lastSeenAt = now;
        pushTokenRepository.save(entity);
        return new NotificationDtos.TokenResponse(entity.id, entity.platform, entity.lastSeenAt);
    }

    @Transactional
    public void revokeToken(String token) {
        pushTokenRepository.findByToken(token).ifPresent(pushTokenRepository::delete);
    }

    // P15-05 - notification preferences

    public List<NotificationDtos.PreferenceResponse> preferences() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = requireUserId();
        Map<String, Boolean> stored = new HashMap<>();
        for (NotificationPreferenceEntity entity : preferenceRepository.findByTenantIdAndUserId(tenantId, userId)) {
            stored.put(key(entity.type, entity.channel), entity.enabled);
        }
        List<NotificationDtos.PreferenceResponse> result = new ArrayList<>();
        NotificationTypes.TYPES.stream().sorted().forEach(type -> NotificationTypes.CHANNELS.stream().sorted()
                .forEach(channel -> result.add(new NotificationDtos.PreferenceResponse(type, channel,
                        stored.getOrDefault(key(type, channel), true)))));
        return result;
    }

    @Transactional
    public List<NotificationDtos.PreferenceResponse> updatePreferences(
            NotificationDtos.PreferencesUpdateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = requireUserId();
        for (NotificationDtos.PreferenceUpdate item : request.items()) {
            if (!NotificationTypes.isKnownType(item.type())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Unknown notification type: " + item.type());
            }
            if (!NotificationTypes.isKnownChannel(item.channel())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Unknown notification channel: " + item.channel());
            }
        }
        for (NotificationDtos.PreferenceUpdate item : request.items()) {
            NotificationPreferenceEntity entity = preferenceRepository
                    .findByTenantIdAndUserIdAndTypeAndChannel(tenantId, userId, item.type(), item.channel())
                    .orElseGet(() -> {
                        NotificationPreferenceEntity created = new NotificationPreferenceEntity();
                        created.id = UUID.randomUUID();
                        created.tenantId = tenantId;
                        created.userId = userId;
                        created.type = item.type();
                        created.channel = item.channel();
                        return created;
                    });
            entity.enabled = item.enabled();
            preferenceRepository.save(entity);
        }
        return preferences();
    }

    public boolean isEnabled(UUID tenantId, UUID userId, String type, String channel) {
        if (userId == null) {
            return false;
        }
        return preferenceRepository.findByTenantIdAndUserIdAndTypeAndChannel(tenantId, userId, type, channel)
                .map(entity -> entity.enabled)
                .orElse(true);
    }

    // P15-04 - notification log

    @Transactional
    public void log(UUID tenantId, UUID userId, String channel, String type, Map<String, Object> payload,
            String status, String error) {
        NotificationLogEntity entity = new NotificationLogEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.userId = userId;
        entity.channel = channel;
        entity.type = type;
        entity.payload = payload;
        entity.status = status;
        entity.sentAt = Instant.now();
        entity.error = error == null || error.length() <= MAX_ERROR_LENGTH
                ? error
                : error.substring(0, MAX_ERROR_LENGTH);
        logRepository.save(entity);
    }

    public List<NotificationDtos.LogResponse> recentLogs() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = requireUserId();
        return logRepository.findTop50ByTenantIdAndUserIdOrderBySentAtDesc(tenantId, userId).stream()
                .map(entity -> new NotificationDtos.LogResponse(entity.id, entity.channel, entity.type,
                        entity.status, entity.sentAt, entity.error))
                .toList();
    }

    private static UUID requireUserId() {
        UUID userId = AuthContext.userId();
        if (userId == null) {
            throw new BusinessRuleException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return userId;
    }

    private static String key(String type, String channel) {
        return type + "|" + channel;
    }
}
