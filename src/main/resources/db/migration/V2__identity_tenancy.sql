CREATE TABLE users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email citext UNIQUE,
    phone varchar(32) UNIQUE,
    password_hash text,
    pin_hash text,
    email_verified_at timestamptz,
    phone_verified_at timestamptz,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    locale varchar(8) NOT NULL DEFAULT 'fr',
    mfa_enabled boolean NOT NULL DEFAULT false,
    platform_role varchar(20),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    archived_at timestamptz
);

CREATE TABLE tenants (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(160) NOT NULL,
    slug varchar(80) NOT NULL UNIQUE,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    plan_code varchar(40),
    trial_ends_at timestamptz,
    settings jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    archived_at timestamptz
);

CREATE TABLE memberships (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role varchar(20) NOT NULL,
    farm_scope uuid[] NOT NULL DEFAULT '{}',
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, user_id)
);

CREATE TABLE farms (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name varchar(160) NOT NULL,
    code varchar(40) NOT NULL,
    country char(2),
    currency char(3) NOT NULL DEFAULT 'XOF',
    timezone varchar(64) NOT NULL DEFAULT 'UTC',
    address text,
    settings jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    archived_at timestamptz,
    UNIQUE (tenant_id, code)
);

CREATE TABLE refresh_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    rotated_from uuid,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE otp_codes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid REFERENCES users(id) ON DELETE CASCADE,
    destination varchar(160) NOT NULL,
    channel varchar(10) NOT NULL,
    code_hash text NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    attempts int NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE invitations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email citext,
    phone varchar(32),
    role varchar(20) NOT NULL,
    farm_scope uuid[] NOT NULL DEFAULT '{}',
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    accepted_at timestamptz,
    invited_by uuid REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (email IS NOT NULL OR phone IS NOT NULL)
);

CREATE TABLE mfa_secrets (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    secret text NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    recovery_codes text[],
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE consents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type varchar(40) NOT NULL,
    version varchar(20) NOT NULL,
    accepted_at timestamptz NOT NULL DEFAULT now(),
    ip inet
);

CREATE TABLE support_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_admin_id uuid NOT NULL REFERENCES users(id),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    reason text NOT NULL,
    started_at timestamptz NOT NULL DEFAULT now(),
    ended_at timestamptz
);

CREATE TRIGGER users_set_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER tenants_set_updated_at BEFORE UPDATE ON tenants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER memberships_set_updated_at BEFORE UPDATE ON memberships
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER farms_set_updated_at BEFORE UPDATE ON farms
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER mfa_secrets_set_updated_at BEFORE UPDATE ON mfa_secrets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
