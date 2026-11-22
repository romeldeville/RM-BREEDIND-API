package com.rmbreeding.api.notifications;

public interface EmailSender {

    void send(String to, String subject, String body);
}
