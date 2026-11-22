package com.rmbreeding.api.notifications;

import com.rmbreeding.api.alerts.AlertRaisedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to alerts through the public module boundary. The push delivery is
 * asynchronous so alert generation never blocks on an external provider, and
 * it happens after commit so a rolled-back alert is never notified.
 */
@Component
class AlertNotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(AlertNotificationListener.class);

    private final AlertNotificationDispatcher dispatcher;

    AlertNotificationListener(AlertNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAlertRaised(AlertRaisedEvent event) {
        try {
            dispatcher.dispatch(event);
        } catch (RuntimeException ex) {
            LOG.warn("Alert push dispatch failed for tenant {}: {}", event.tenantId(), ex.getMessage());
        }
    }
}
