package com.rmbreeding.api.notifications;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
class NotificationConfig {

    @Bean
    PushSender pushSender(NotificationProperties properties, ObjectMapper objectMapper) {
        NotificationProperties.Push push = properties.getPush();
        if (push.isEnabled() && notBlank(push.getProjectId()) && notBlank(push.getServiceAccountJson())) {
            return new FcmPushSender(properties, objectMapper);
        }
        return new LoggingPushSender();
    }

    @Bean
    EmailSender emailSender(NotificationProperties properties) {
        NotificationProperties.Email email = properties.getEmail();
        if (email.isEnabled() && notBlank(email.getHost())) {
            return new SmtpEmailSender(email);
        }
        return new LoggingEmailSender();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
