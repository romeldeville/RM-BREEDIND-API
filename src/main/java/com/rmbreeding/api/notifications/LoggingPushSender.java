package com.rmbreeding.api.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Fallback used when no FCM service account is configured. The message is
 * logged so local development and tests do not need any external provider.
 */
public class LoggingPushSender implements PushSender {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public void send(String token, String title, String body, Map<String, String> data) {
        LOG.info("Push notification (no provider configured) token={} title={} data={}",
                mask(token), title, data);
    }

    static String mask(String token) {
        if (token == null || token.length() <= 8) {
            return "****";
        }
        return token.substring(0, 4) + "…" + token.substring(token.length() - 4);
    }
}
