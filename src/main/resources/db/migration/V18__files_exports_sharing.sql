-- Phase 14 - fichiers, exports et partage

ALTER TABLE attachments
    ADD COLUMN IF NOT EXISTS confirmed_at timestamptz,
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_attachments_tenant_entity
    ON attachments (tenant_id, entity_type, entity_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_attachments_tenant_unconfirmed
    ON attachments (tenant_id, created_at)
    WHERE confirmed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_attachments_tenant_deleted
    ON attachments (tenant_id, deleted_at)
    WHERE deleted_at IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_attachments_bucket_key
    ON attachments (bucket, storage_key);

CREATE TABLE IF NOT EXISTS share_links (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    attachment_id uuid REFERENCES attachments(id) ON DELETE SET NULL,
    bucket varchar(80) NOT NULL,
    storage_key text NOT NULL,
    filename varchar(255),
    mime_type varchar(120),
    size_bytes bigint,
    entity_type varchar(60),
    entity_id uuid,
    created_by uuid,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    access_count integer NOT NULL DEFAULT 0,
    last_accessed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_share_links_tenant_created
    ON share_links (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_share_links_tenant_entity
    ON share_links (tenant_id, entity_type, entity_id);

CREATE TABLE IF NOT EXISTS export_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    type varchar(40) NOT NULL,
    status varchar(20) NOT NULL,
    params jsonb NOT NULL DEFAULT '{}'::jsonb,
    bucket varchar(80),
    storage_key text,
    filename varchar(255),
    mime_type varchar(120),
    size_bytes bigint,
    row_count integer,
    error_message text,
    requested_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    completed_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_export_jobs_tenant_created
    ON export_jobs (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_export_jobs_status
    ON export_jobs (status, created_at);

DO $$
DECLARE
    t text;
    tables text[] := ARRAY['share_links', 'export_jobs'];
BEGIN
    FOREACH t IN ARRAY tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_setting(''app.tenant_id'', true)::uuid) WITH CHECK (tenant_id = current_setting(''app.tenant_id'', true)::uuid)',
            t);
    END LOOP;
END
$$;
