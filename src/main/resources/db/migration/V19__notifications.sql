-- Phase 15 - notifications push et e-mail

CREATE INDEX IF NOT EXISTS idx_push_tokens_tenant_user
    ON push_tokens (tenant_id, user_id);

CREATE INDEX IF NOT EXISTS idx_push_tokens_user
    ON push_tokens (user_id);

CREATE INDEX IF NOT EXISTS idx_notification_preferences_tenant_user
    ON notification_preferences (tenant_id, user_id);

CREATE INDEX IF NOT EXISTS idx_notification_log_tenant_user_sent
    ON notification_log (tenant_id, user_id, sent_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_log_tenant_type
    ON notification_log (tenant_id, type, sent_at DESC);
