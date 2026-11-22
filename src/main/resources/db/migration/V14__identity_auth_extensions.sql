ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name varchar(160);
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts int NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until timestamptz;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS country char(2);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS currency char(3) NOT NULL DEFAULT 'XOF';
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS tenant_id uuid REFERENCES tenants(id) ON DELETE SET NULL;

UPDATE users SET full_name = split_part(email, '@', 1) WHERE full_name IS NULL;
UPDATE tenants SET country = 'CI' WHERE country IS NULL;

CREATE TABLE email_verification_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE password_reset_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE mfa_challenges (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash text NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

DROP POLICY IF EXISTS tenant_isolation ON memberships;
CREATE POLICY tenant_isolation ON memberships
    USING (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
        OR user_id = NULLIF(current_setting('app.auth_user_id', true), '')::uuid
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
        OR user_id = NULLIF(current_setting('app.auth_user_id', true), '')::uuid
    );
