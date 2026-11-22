CREATE TABLE alerts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    farm_id uuid NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    type varchar(60) NOT NULL,
    priority varchar(20) NOT NULL,
    title varchar(200) NOT NULL,
    message text,
    due_date date,
    entity_type varchar(40),
    entity_id uuid,
    status varchar(20) NOT NULL DEFAULT 'OPEN',
    postpone_reason text,
    assigned_role varchar(20),
    dedup_key varchar(200) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    closed_at timestamptz,
    closed_by uuid,
    UNIQUE (tenant_id, dedup_key)
);

CREATE TABLE tasks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    farm_id uuid NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    title varchar(200) NOT NULL,
    description text,
    due_date date,
    assigned_to uuid,
    recurrence varchar(40),
    status varchar(20) NOT NULL DEFAULT 'OPEN',
    created_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz
);

CREATE TABLE audit_log (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid,
    actor_id uuid,
    action varchar(80) NOT NULL,
    entity_type varchar(60),
    entity_id uuid,
    before jsonb,
    after jsonb,
    ip inet,
    user_agent text,
    occurred_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE domain_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    farm_id uuid,
    aggregate_type varchar(60) NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type varchar(80) NOT NULL,
    event_version int NOT NULL DEFAULT 1,
    occurred_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now(),
    actor_id uuid,
    client_id varchar(80),
    idempotency_key varchar(200) NOT NULL UNIQUE,
    server_seq bigserial,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    corrects_event_id uuid,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE sync_clients (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    device_id varchar(80) NOT NULL,
    last_cursor bigint NOT NULL DEFAULT 0,
    last_seen_at timestamptz NOT NULL,
    UNIQUE (tenant_id, device_id)
);

CREATE TABLE sync_conflicts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    entity_type varchar(60) NOT NULL,
    entity_id uuid,
    client_event_id uuid,
    server_event_id uuid,
    resolution varchar(40),
    resolved_at timestamptz,
    payload jsonb
);

CREATE TABLE attachments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    entity_type varchar(60) NOT NULL,
    entity_id uuid NOT NULL,
    bucket varchar(80) NOT NULL,
    storage_key text NOT NULL,
    filename varchar(255),
    mime_type varchar(120),
    size_bytes bigint,
    checksum varchar(64),
    uploaded_by uuid,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE notification_log (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id uuid,
    channel varchar(20) NOT NULL,
    type varchar(60) NOT NULL,
    payload jsonb,
    status varchar(20) NOT NULL,
    sent_at timestamptz,
    error text
);

CREATE TABLE notification_preferences (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    type varchar(60) NOT NULL,
    channel varchar(20) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    UNIQUE (user_id, type, channel)
);

CREATE TABLE push_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    token text NOT NULL UNIQUE,
    platform varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz
);

CREATE TABLE idempotency_keys (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    key varchar(200) NOT NULL,
    request_hash varchar(64) NOT NULL,
    status int,
    response_body text,
    response_content_type varchar(120),
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    UNIQUE (tenant_id, key)
);
