package com.rmbreeding.api.notifications;

public class PushDeliveryException extends RuntimeException {

    public PushDeliveryException(String message) {
        super(message);
    }

    public PushDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
