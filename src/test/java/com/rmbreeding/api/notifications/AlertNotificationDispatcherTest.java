package com.rmbreeding.api.notifications;

import com.rmbreeding.api.alerts.AlertRaisedEvent;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertNotificationDispatcherTest {

    @Mock
    private PushTokenRepository pushTokenRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PushSender pushSender;

    private AlertNotificationDispatcher dispatcher;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dispatcher = new AlertNotificationDispatcher(pushTokenRepository, notificationService, pushSender);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void sendsPushToEnabledDevicesAndLogsDelivery() {
        PushTokenEntity token = token("token-1");
        when(pushTokenRepository.findByTenantId(tenantId)).thenReturn(List.of(token));
        when(notificationService.isEnabled(tenantId, userId, "FARROWING_DUE", "PUSH")).thenReturn(true);

        int sent = dispatcher.dispatch(event("CRITICAL"));

        assertThat(sent).isEqualTo(1);
        verify(pushSender).send(eq("token-1"), eq("Mise bas prévue"), eq("Mise bas le 20/03/2026"),
                anyMap());
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).log(eq(tenantId), eq(userId), eq("PUSH"), eq("FARROWING_DUE"),
                payload.capture(), eq("SENT"), eq(null));
        assertThat(payload.getValue()).containsEntry("alertType", "FARROWING_DUE");
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void ignoresInfoAlerts() {
        int sent = dispatcher.dispatch(event("INFO"));

        assertThat(sent).isZero();
        verify(pushTokenRepository, never()).findByTenantId(any());
        verify(pushSender, never()).send(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void skipsDevicesThatDisabledPush() {
        when(pushTokenRepository.findByTenantId(tenantId)).thenReturn(List.of(token("token-1")));
        when(notificationService.isEnabled(tenantId, userId, "FARROWING_DUE", "PUSH")).thenReturn(false);

        int sent = dispatcher.dispatch(event("IMPORTANT"));

        assertThat(sent).isZero();
        verify(pushSender, never()).send(anyString(), anyString(), anyString(), anyMap());
        verify(notificationService, never()).log(any(), any(), anyString(), anyString(), anyMap(),
                anyString(), any());
    }

    @Test
    void logsFailureWhenProviderRejectsToken() {
        when(pushTokenRepository.findByTenantId(tenantId)).thenReturn(List.of(token("token-1")));
        when(notificationService.isEnabled(tenantId, userId, "FARROWING_DUE", "PUSH")).thenReturn(true);
        doThrow(new PushDeliveryException("invalid token"))
                .when(pushSender).send(anyString(), anyString(), anyString(), anyMap());

        int sent = dispatcher.dispatch(event("CRITICAL"));

        assertThat(sent).isZero();
        verify(notificationService).log(eq(tenantId), eq(userId), eq("PUSH"), eq("FARROWING_DUE"),
                anyMap(), eq("FAILED"), eq("invalid token"));
    }

    private AlertRaisedEvent event(String priority) {
        return new AlertRaisedEvent(tenantId, UUID.randomUUID(), "FARROWING_DUE", priority,
                "Mise bas prévue", "Mise bas le 20/03/2026", LocalDate.parse("2026-03-20"),
                "cycle", UUID.randomUUID());
    }

    private PushTokenEntity token(String value) {
        PushTokenEntity entity = new PushTokenEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.userId = userId;
        entity.token = value;
        entity.platform = "ANDROID";
        return entity;
    }
}
