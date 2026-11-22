CREATE TABLE sync_projections (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    aggregate_type varchar(60) NOT NULL,
    aggregate_id uuid NOT NULL,
    event_id uuid NOT NULL,
    server_seq bigint NOT NULL,
    event_type varchar(80) NOT NULL,
    occurred_at timestamptz NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, aggregate_type, aggregate_id)
);

CREATE INDEX idx_sync_projections_type ON sync_projections (tenant_id, aggregate_type);

ALTER TABLE sync_conflicts
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

CREATE INDEX idx_sync_conflicts_created ON sync_conflicts (tenant_id, created_at DESC);

ALTER TABLE sync_projections ENABLE ROW LEVEL SECURITY;
ALTER TABLE sync_projections FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON sync_projections
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);
