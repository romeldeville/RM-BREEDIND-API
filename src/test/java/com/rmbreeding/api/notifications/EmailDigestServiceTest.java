package com.rmbreeding.api.notifications;

import com.rmbreeding.api.alerts.AlertsDtos;
import com.rmbreeding.api.alerts.AlertsService;
import com.rmbreeding.api.identity.IdentityDtos;
import com.rmbreeding.api.identity.NotificationDirectory;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailDigestServiceTest {

    @Mock
    private NotificationDirectory directory;
    @Mock
    private AlertsService alertsService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailSender emailSender;

    private EmailDigestService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void sendsOneDigestPerRecipientAndLogsIt() {
        service = new EmailDigestService(directory, alertsService, notificationService, emailSender);
        when(alertsService.openAlerts()).thenReturn(List.of(alert("CRITICAL", "Mise bas prévue")));
        when(directory.recipients(tenantId)).thenReturn(List.of(recipient("owner@farm.test")));
        when(notificationService.isEnabled(tenantId, userId, "DAILY_DIGEST", "EMAIL")).thenReturn(true);

        TenantContext.setTenantId(tenantId);
        int sent = service.sendTenant();
        TenantContext.clear();

        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq("owner@farm.test"), eq("RM Breeding - 1 alerte(s) en cours"),
                body.capture());
        assertThat(body.getValue()).contains("Mise bas prévue").contains("20/03/2026");
        verify(notificationService).log(eq(tenantId), eq(userId), eq("EMAIL"), eq("DAILY_DIGEST"),
                anyMap(), eq("SENT"), eq(null));
    }

    @Test
    void skipsRecipientsThatDisabledTheDigest() {
        service = new EmailDigestService(directory, alertsService, notificationService, emailSender);
        when(alertsService.openAlerts()).thenReturn(List.of(alert("CRITICAL", "Mise bas prévue")));
        when(directory.recipients(tenantId)).thenReturn(List.of(recipient("owner@farm.test")));
        when(notificationService.isEnabled(tenantId, userId, "DAILY_DIGEST", "EMAIL")).thenReturn(false);

        TenantContext.setTenantId(tenantId);
        int sent = service.sendTenant();
        TenantContext.clear();

        assertThat(sent).isZero();
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void skipsDigestWhenNoAlertIsOpen() {
        service = new EmailDigestService(directory, alertsService, notificationService, emailSender);
        when(alertsService.openAlerts()).thenReturn(List.of());

        TenantContext.setTenantId(tenantId);
        int sent = service.sendTenant();
        TenantContext.clear();

        assertThat(sent).isZero();
        verify(directory, never()).recipients(any());
    }

    @Test
    void logsFailedDelivery() {
        service = new EmailDigestService(directory, alertsService, notificationService, emailSender);
        when(alertsService.openAlerts()).thenReturn(List.of(alert("IMPORTANT", "Stock bas")));
        when(directory.recipients(tenantId)).thenReturn(List.of(recipient("owner@farm.test")));
        when(notificationService.isEnabled(tenantId, userId, "DAILY_DIGEST", "EMAIL")).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp down"))
                .when(emailSender).send(anyString(), anyString(), anyString());

        TenantContext.setTenantId(tenantId);
        int sent = service.sendTenant();
        TenantContext.clear();

        assertThat(sent).isZero();
        verify(notificationService).log(eq(tenantId), eq(userId), eq("EMAIL"), eq("DAILY_DIGEST"),
                anyMap(), eq("FAILED"), eq("smtp down"));
    }

    @Test
    void runsForEveryTenantAndRestoresContext() {
        service = new EmailDigestService(directory, alertsService, notificationService, emailSender);
        UUID secondTenant = UUID.randomUUID();
        when(directory.tenantIds()).thenReturn(List.of(tenantId, secondTenant));
        when(alertsService.openAlerts()).thenReturn(List.of());
        TenantContext.setTenantId(UUID.randomUUID());

        int sent = service.sendAllTenants();

        assertThat(sent).isZero();
        assertThat(TenantContext.getTenantId()).isNotNull();
        TenantContext.clear();
    }

    @Test
    void digestBodyListsPriorityAndMessage() {
        String body = EmailDigestService.body(List.of(alert("CRITICAL", "Mise bas prévue")));

        assertThat(body).contains("[CRITICAL] Mise bas prévue").contains("Le troupeau");
    }

    private AlertsDtos.AlertResponse alert(String priority, String title) {
        return new AlertsDtos.AlertResponse(UUID.randomUUID(), UUID.randomUUID(), "FARROWING_DUE",
                priority, title, "Le troupeau", LocalDate.parse("2026-03-20"), "OPEN", "cycle",
                UUID.randomUUID(), null, null, Instant.now(), null);
    }

    private IdentityDtos.NotificationRecipient recipient(String email) {
        return new IdentityDtos.NotificationRecipient(userId, email, "Éleveur");
    }
}
