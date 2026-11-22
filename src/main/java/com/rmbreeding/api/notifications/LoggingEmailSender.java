package com.rmbreeding.api.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback used when SMTP is not configured. The digest is still traced in
 * {@code notification_log}, which keeps the scheduled job observable.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        LOG.info("E-mail (no SMTP configured) to={} subject={} lines={}", to, subject,
                body == null ? 0 : body.lines().count());
    }
}
