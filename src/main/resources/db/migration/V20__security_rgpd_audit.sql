-- Phase 16 - sécurité, RGPD et audit

-- Journal d'audit : contexte de session support et métadonnées de requête.
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS support_session_id uuid;

CREATE INDEX IF NOT EXISTS idx_audit_occurred ON audit_log (occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_tenant_occurred ON audit_log (tenant_id, occurred_at DESC);

-- Sessions support : durée de vie explicite et révocation des jetons associés.
ALTER TABLE support_sessions ADD COLUMN IF NOT EXISTS expires_at timestamptz;
UPDATE support_sessions
SET expires_at = started_at + interval '2 hours'
WHERE expires_at IS NULL;
ALTER TABLE support_sessions ALTER COLUMN expires_at SET NOT NULL;

ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS support_session_id uuid
    REFERENCES support_sessions(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_support_session
    ON refresh_tokens (support_session_id)
    WHERE support_session_id IS NOT NULL;

-- Suppression différée et réversible d'un tenant (30 jours).
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS delete_requested_at timestamptz;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS delete_after timestamptz;

CREATE INDEX IF NOT EXISTS idx_tenants_pending_deletion
    ON tenants (delete_after)
    WHERE status = 'PENDING_DELETION';

-- Index de purge / conservation.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expiry ON refresh_tokens (expires_at, revoked_at);
CREATE INDEX IF NOT EXISTS idx_otp_created_at ON otp_codes (created_at);
CREATE INDEX IF NOT EXISTS idx_email_verification_created_at ON email_verification_tokens (created_at);
CREATE INDEX IF NOT EXISTS idx_password_reset_created_at ON password_reset_tokens (created_at);

-- Revue RLS : toute table portant tenant_id est protégée automatiquement.
-- audit_log est volontairement exclu : il contient aussi des événements plateforme
-- (tenant_id nul) et n'est jamais exposé sans filtre applicatif.
DO $$
DECLARE
    t text;
BEGIN
    FOR t IN
        SELECT table_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND column_name = 'tenant_id'
          AND table_name <> 'audit_log'
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_setting(''app.tenant_id'', true)::uuid) WITH CHECK (tenant_id = current_setting(''app.tenant_id'', true)::uuid)',
            t);
    END LOOP;
END
$$;
