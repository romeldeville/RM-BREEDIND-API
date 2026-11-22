package com.rmbreeding.api.notifications;

import com.rmbreeding.api.alerts.AlertsDtos;
import com.rmbreeding.api.alerts.AlertsService;
import com.rmbreeding.api.identity.IdentityDtos;
import com.rmbreeding.api.identity.NotificationDirectory;
import com.rmbreeding.api.shared.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * P15-06 - daily recap sent by e-mail when no push device is available. Each
 * active member of a tenant receives at most one e-mail per run.
 */
@Service
public class EmailDigestService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailDigestService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NotificationDirectory directory;
    private final AlertsService alertsService;
    private final NotificationService notificationService;
    private final EmailSender emailSender;

    public EmailDigestService(NotificationDirectory directory, AlertsService alertsService,
            NotificationService notificationService, EmailSender emailSender) {
        this.directory = directory;
        this.alertsService = alertsService;
        this.notificationService = notificationService;
        this.emailSender = emailSender;
    }

    public int sendAllTenants() {
        int total = 0;
        for (UUID tenantId : directory.tenantIds()) {
            UUID previousTenant = TenantContext.getTenantId();
            TenantContext.setTenantId(tenantId);
            try {
                total += sendTenant();
            } catch (RuntimeException ex) {
                LOG.warn("Notification digest failed for tenant {}: {}", tenantId, ex.getMessage());
            } finally {
                if (previousTenant == null) {
                    TenantContext.clear();
                } else {
                    TenantContext.setTenantId(previousTenant);
                }
            }
        }
        return total;
    }

    public int sendTenant() {
        UUID tenantId = TenantContext.requireTenantId();
        List<AlertsDtos.AlertResponse> alerts = alertsService.openAlerts();
        if (alerts.isEmpty()) {
            return 0;
        }
        String subject = "RM Breeding - " + alerts.size() + " alerte(s) en cours";
        String body = body(alerts);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alertCount", alerts.size());
        payload.put("types", alerts.stream().map(AlertsDtos.AlertResponse::type).distinct().toList());

        int sent = 0;
        for (IdentityDtos.NotificationRecipient recipient : directory.recipients(tenantId)) {
            if (!notificationService.isEnabled(tenantId, recipient.userId(),
                    NotificationTypes.DAILY_DIGEST, NotificationTypes.CHANNEL_EMAIL)) {
                continue;
            }
            try {
                emailSender.send(recipient.email(), subject, body);
                notificationService.log(tenantId, recipient.userId(), NotificationTypes.CHANNEL_EMAIL,
                        NotificationTypes.DAILY_DIGEST, new LinkedHashMap<>(payload), "SENT", null);
                sent++;
            } catch (RuntimeException ex) {
                LOG.warn("Digest e-mail failed for tenant {}: {}", tenantId, ex.getMessage());
                notificationService.log(tenantId, recipient.userId(), NotificationTypes.CHANNEL_EMAIL,
                        NotificationTypes.DAILY_DIGEST, new LinkedHashMap<>(payload), "FAILED", ex.getMessage());
            }
        }
        return sent;
    }

    static String body(List<AlertsDtos.AlertResponse> alerts) {
        StringBuilder builder = new StringBuilder();
        builder.append("Bonjour,\n\n");
        builder.append("Voici le récapitulatif des alertes en cours sur votre exploitation :\n\n");
        for (AlertsDtos.AlertResponse alert : alerts) {
            builder.append("- [").append(alert.priority()).append("] ").append(alert.title());
            if (alert.dueDate() != null) {
                builder.append(" (échéance ").append(DATE.format(alert.dueDate())).append(")");
            }
            builder.append('\n');
            if (alert.message() != null && !alert.message().isBlank()) {
                builder.append("  ").append(alert.message()).append('\n');
            }
        }
        builder.append("\nOuvrez l'application RM Breeding pour traiter ces alertes.\n");
        return builder.toString();
    }
}
