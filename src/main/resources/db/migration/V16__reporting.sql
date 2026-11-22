CREATE TABLE dashboard_snapshots (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    farm_id uuid REFERENCES farms(id) ON DELETE SET NULL,
    herd_size bigint NOT NULL DEFAULT 0,
    open_alerts bigint NOT NULL DEFAULT 0,
    births_this_month bigint NOT NULL DEFAULT 0,
    average_daily_gain numeric(12, 3),
    gross_margin numeric(18, 2),
    refreshed_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_dashboard_snapshots_tenant
    ON dashboard_snapshots (tenant_id, refreshed_at DESC);

ALTER TABLE dashboard_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE dashboard_snapshots FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON dashboard_snapshots
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);
