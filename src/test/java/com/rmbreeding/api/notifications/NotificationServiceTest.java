package com.rmbreeding.api.notifications;

import com.rmbreeding.api.identity.AuthContext;
import com.rmbreeding.api.identity.AuthPrincipal;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private PushTokenRepository pushTokenRepository;
    @Mock
    private NotificationPreferenceRepository preferenceRepository;
    @Mock
    private NotificationLogRepository logRepository;

    private NotificationService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NotificationService(pushTokenRepository, preferenceRepository, logRepository);
        TenantContext.setTenantId(tenantId);
        AuthContext.set(new AuthPrincipal(userId, tenantId, List.of("OWNER"), false, null));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        AuthContext.clear();
    }

    // P15-02

    @Test
    void registersNewDeviceToken() {
        when(pushTokenRepository.findByToken("device-token")).thenReturn(Optional.empty());
        when(pushTokenRepository.save(any(PushTokenEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationDtos.TokenResponse response = service.registerToken(
                new NotificationDtos.TokenRequest("device-token", "android"));

        assertThat(response.id()).isNotNull();
        assertThat(response.platform()).isEqualTo("ANDROID");
        ArgumentCaptor<PushTokenEntity> captor = ArgumentCaptor.forClass(PushTokenEntity.class);
        verify(pushTokenRepository).save(captor.capture());
        assertThat(captor.getValue().tenantId).isEqualTo(tenantId);
        assertThat(captor.getValue().userId).isEqualTo(userId);
        assertThat(captor.getValue().createdAt).isNotNull();
        assertThat(captor.getValue().lastSeenAt).isNotNull();
    }

    @Test
    void refreshesExistingDeviceToken() {
        PushTokenEntity existing = token("device-token", userId);
        Instant created = existing.createdAt;
        when(pushTokenRepository.findByToken("device-token")).thenReturn(Optional.of(existing));
        when(pushTokenRepository.save(existing)).thenReturn(existing);

        service.registerToken(new NotificationDtos.TokenRequest("device-token", "IOs"));

        assertThat(existing.platform).isEqualTo("IOS");
        assertThat(existing.createdAt).isEqualTo(created);
        assertThat(existing.lastSeenAt).isAfter(created);
    }

    @Test
    void revokesExistingToken() {
        PushTokenEntity existing = token("device-token", userId);
        when(pushTokenRepository.findByToken("device-token")).thenReturn(Optional.of(existing));

        service.revokeToken("device-token");

        verify(pushTokenRepository).delete(existing);
    }

    // P15-05

    @Test
    void preferencesDefaultToEnabled() {
        when(preferenceRepository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(List.of());

        List<NotificationDtos.PreferenceResponse> preferences = service.preferences();

        assertThat(preferences).hasSize(NotificationTypes.TYPES.size() * NotificationTypes.CHANNELS.size());
        assertThat(preferences).allMatch(NotificationDtos.PreferenceResponse::enabled);
        assertThat(preferences).anyMatch(item -> item.type().equals(NotificationTypes.DAILY_DIGEST)
                && item.channel().equals(NotificationTypes.CHANNEL_EMAIL));
    }

    @Test
    void preferencesExposeStoredValues() {
        NotificationPreferenceEntity stored = preference("FARROWING_DUE", "PUSH", false);
        when(preferenceRepository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(List.of(stored));

        List<NotificationDtos.PreferenceResponse> preferences = service.preferences();

        assertThat(preferences).anyMatch(item -> item.type().equals("FARROWING_DUE")
                && item.channel().equals("PUSH") && !item.enabled());
        assertThat(preferences).anyMatch(item -> item.type().equals("FARROWING_DUE")
                && item.channel().equals("EMAIL") && item.enabled());
    }

    @Test
    void updatesPreference() {
        when(preferenceRepository.findByTenantIdAndUserIdAndTypeAndChannel(tenantId, userId,
                "FARROWING_DUE", "PUSH")).thenReturn(Optional.empty());
        when(preferenceRepository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(List.of());

        service.updatePreferences(new NotificationDtos.PreferencesUpdateRequest(List.of(
                new NotificationDtos.PreferenceUpdate("FARROWING_DUE", "PUSH", false))));

        ArgumentCaptor<NotificationPreferenceEntity> captor = ArgumentCaptor
                .forClass(NotificationPreferenceEntity.class);
        verify(preferenceRepository).save(captor.capture());
        assertThat(captor.getValue().tenantId).isEqualTo(tenantId);
        assertThat(captor.getValue().userId).isEqualTo(userId);
        assertThat(captor.getValue().enabled).isFalse();
    }

    @Test
    void rejectsUnknownNotificationType() {
        assertThatThrownBy(() -> service.updatePreferences(new NotificationDtos.PreferencesUpdateRequest(
                List.of(new NotificationDtos.PreferenceUpdate("UNKNOWN", "PUSH", true)))))
                .isInstanceOf(BusinessRuleException.class);

        verify(preferenceRepository, never()).save(any(NotificationPreferenceEntity.class));
    }

    @Test
    void rejectsUnknownNotificationChannel() {
        assertThatThrownBy(() -> service.updatePreferences(new NotificationDtos.PreferencesUpdateRequest(
                List.of(new NotificationDtos.PreferenceUpdate("FARROWING_DUE", "SMS", true)))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void isEnabledDefaultsToTrueAndUsesStoredPreference() {
        assertThat(service.isEnabled(tenantId, userId, "FARROWING_DUE", "PUSH")).isTrue();

        when(preferenceRepository.findByTenantIdAndUserIdAndTypeAndChannel(tenantId, userId,
                "FARROWING_DUE", "PUSH"))
                .thenReturn(Optional.of(preference("FARROWING_DUE", "PUSH", false)));

        assertThat(service.isEnabled(tenantId, userId, "FARROWING_DUE", "PUSH")).isFalse();
    }

    @Test
    void requiresAuthenticatedUserForPreferences() {
        AuthContext.clear();

        assertThatThrownBy(() -> service.preferences()).isInstanceOf(BusinessRuleException.class);
    }

    // P15-04

    @Test
    void writesNotificationLogEntry() {
        when(logRepository.save(any(NotificationLogEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.log(tenantId, userId, "PUSH", "FARROWING_DUE", Map.of("alertType", "FARROWING_DUE"),
                "SENT", null);

        ArgumentCaptor<NotificationLogEntity> captor = ArgumentCaptor.forClass(NotificationLogEntity.class);
        verify(logRepository).save(captor.capture());
        NotificationLogEntity entry = captor.getValue();
        assertThat(entry.tenantId).isEqualTo(tenantId);
        assertThat(entry.status).isEqualTo("SENT");
        assertThat(entry.sentAt).isNotNull();
        assertThat(entry.payload).containsEntry("alertType", "FARROWING_DUE");
    }

    @Test
    void truncatesLogError() {
        when(logRepository.save(any(NotificationLogEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.log(tenantId, userId, "PUSH", "FARROWING_DUE", Map.of(), "FAILED", "x".repeat(900));

        ArgumentCaptor<NotificationLogEntity> captor = ArgumentCaptor.forClass(NotificationLogEntity.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().error).hasSize(500);
    }

    @Test
    void listsRecentLogsForCurrentUser() {
        NotificationLogEntity entry = new NotificationLogEntity();
        entry.id = UUID.randomUUID();
        entry.channel = "PUSH";
        entry.type = "FARROWING_DUE";
        entry.status = "SENT";
        entry.sentAt = Instant.now();
        when(logRepository.findTop50ByTenantIdAndUserIdOrderBySentAtDesc(tenantId, userId))
                .thenReturn(List.of(entry));

        List<NotificationDtos.LogResponse> logs = service.recentLogs();

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).type()).isEqualTo("FARROWING_DUE");
    }

    private PushTokenEntity token(String value, UUID owner) {
        PushTokenEntity entity = new PushTokenEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.userId = owner;
        entity.token = value;
        entity.platform = "ANDROID";
        entity.createdAt = Instant.now().minusSeconds(60);
        entity.lastSeenAt = entity.createdAt;
        return entity;
    }

    private NotificationPreferenceEntity preference(String type, String channel, boolean enabled) {
        NotificationPreferenceEntity entity = new NotificationPreferenceEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.userId = userId;
        entity.type = type;
        entity.channel = channel;
        entity.enabled = enabled;
        return entity;
    }
}
