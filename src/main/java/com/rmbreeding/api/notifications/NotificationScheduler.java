package com.rmbreeding.api.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationScheduler.class);

    private final NotificationProperties properties;
    private final EmailDigestService digestService;

    public NotificationScheduler(NotificationProperties properties, EmailDigestService digestService) {
        this.properties = properties;
        this.digestService = digestService;
    }

    @Scheduled(cron = "${app.notifications.digest.cron:0 0 7 * * *}")
    public void sendDailyDigest() {
        if (!properties.getDigest().isEnabled()) {
            return;
        }
        int sent = digestService.sendAllTenants();
        if (sent > 0) {
            LOG.info("Sent {} notification digest e-mail(s)", sent);
        }
    }
}
