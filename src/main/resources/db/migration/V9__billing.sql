CREATE TABLE plans (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(40) NOT NULL UNIQUE,
    name varchar(120) NOT NULL,
    price_cents int NOT NULL DEFAULT 0,
    currency char(3) NOT NULL,
    interval varchar(20) NOT NULL DEFAULT 'MONTH',
    trial_days int NOT NULL DEFAULT 14,
    quotas jsonb NOT NULL DEFAULT '{}'::jsonb,
    stripe_price_id varchar(120),
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE subscriptions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    plan_id uuid NOT NULL REFERENCES plans(id),
    provider varchar(20) NOT NULL,
    provider_subscription_id varchar(120),
    status varchar(20) NOT NULL,
    current_period_start timestamptz,
    current_period_end timestamptz,
    cancel_at timestamptz,
    trial_ends_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE invoices (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    subscription_id uuid REFERENCES subscriptions(id) ON DELETE SET NULL,
    number varchar(60) NOT NULL UNIQUE,
    amount_cents int NOT NULL,
    currency char(3) NOT NULL,
    status varchar(20) NOT NULL,
    issued_at timestamptz NOT NULL,
    paid_at timestamptz,
    pdf_key text,
    provider_invoice_id varchar(120)
);

CREATE TABLE payment_transactions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    subscription_id uuid REFERENCES subscriptions(id) ON DELETE SET NULL,
    provider varchar(20) NOT NULL,
    provider_ref varchar(160) NOT NULL UNIQUE,
    amount_cents int NOT NULL,
    currency char(3) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE billing_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    provider varchar(20) NOT NULL,
    event_id varchar(160) NOT NULL UNIQUE,
    type varchar(80) NOT NULL,
    payload jsonb NOT NULL,
    processed_at timestamptz
);

CREATE TABLE quota_usage (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    quota_key varchar(60) NOT NULL,
    used int NOT NULL DEFAULT 0,
    limit_value int,
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, quota_key)
);

CREATE TABLE announcements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title varchar(200) NOT NULL,
    message text NOT NULL,
    severity varchar(20) NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz,
    active boolean NOT NULL DEFAULT true
);

CREATE TRIGGER subscriptions_set_updated_at BEFORE UPDATE ON subscriptions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER payment_transactions_set_updated_at BEFORE UPDATE ON payment_transactions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
