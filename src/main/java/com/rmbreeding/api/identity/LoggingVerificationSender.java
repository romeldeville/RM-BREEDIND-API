package com.rmbreeding.api.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingVerificationSender implements VerificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingVerificationSender.class);

    @Override
    public void sendEmailVerification(String email, String fullName, String token) {
        log.info("email-verification to={} fullName={} token={}", email, fullName, token);
    }

    @Override
    public void sendPasswordReset(String email, String fullName, String token) {
        log.info("password-reset to={} fullName={} token={}", email, fullName, token);
    }

    @Override
    public void sendOtp(String destination, String channel, String code) {
        log.info("otp channel={} destination={} code={}", channel, destination, code);
    }
}
