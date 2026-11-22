package com.rmbreeding.api.notifications;

import java.util.Map;

public interface PushSender {

    void send(String token, String title, String body, Map<String, String> data);
}
