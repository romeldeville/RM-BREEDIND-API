package com.rmbreeding.api.identity;

public interface VerificationSender {

    void sendEmailVerification(String email, String fullName, String token);

    void sendPasswordReset(String email, String fullName, String token);

    void sendOtp(String destination, String channel, String code);
}
