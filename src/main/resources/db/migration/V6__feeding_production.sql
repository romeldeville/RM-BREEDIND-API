CREATE TABLE feeds (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name varchar(160) NOT NULL,
    type varchar(40),
    unit varchar(20) NOT NULL,
    price numeric(14,2),
    supplier varchar(160),
    archived_at timestamptz
);

CREATE TABLE feed_receipts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    feed_id uuid NOT NULL REFERENCES feeds(id) ON DELETE CASCADE,
    quantity numeric(12,3) NOT NULL CHECK (quantity > 0),
    unit_price numeric(14,2),
    received_at timestamptz NOT NULL,
    supplier varchar(160),
    expiry_date date,
    notes text
);

CREATE TABLE feed_distributions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    feed_id uuid NOT NULL REFERENCES feeds(id) ON DELETE CASCADE,
    lot_id uuid REFERENCES lots(id) ON DELETE CASCADE,
    animal_id uuid REFERENCES animals(id) ON DELETE CASCADE,
    quantity numeric(12,3) NOT NULL CHECK (quantity > 0),
    distributed_at timestamptz NOT NULL,
    actor_id uuid,
    plan_id uuid REFERENCES feeding_plans(id) ON DELETE SET NULL,
    notes text,
    CHECK (lot_id IS NOT NULL OR animal_id IS NOT NULL)
);

CREATE TABLE feed_stocks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    feed_id uuid NOT NULL UNIQUE REFERENCES feeds(id) ON DELETE CASCADE,
    quantity numeric(12,3) NOT NULL DEFAULT 0,
    average_price numeric(14,2),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE feed_costs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    lot_id uuid REFERENCES lots(id) ON DELETE CASCADE,
    animal_id uuid REFERENCES animals(id) ON DELETE CASCADE,
    amount numeric(14,2) NOT NULL,
    period_start date,
    period_end date,
    CHECK (lot_id IS NOT NULL OR animal_id IS NOT NULL)
);

CREATE TABLE weighings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    animal_id uuid REFERENCES animals(id) ON DELETE CASCADE,
    lot_id uuid REFERENCES lots(id) ON DELETE CASCADE,
    weighed_at timestamptz NOT NULL,
    weight_kg numeric(10,3) NOT NULL CHECK (weight_kg > 0),
    sample_count int,
    average_weight numeric(10,3),
    CHECK (animal_id IS NOT NULL OR lot_id IS NOT NULL)
);

CREATE TABLE egg_collections (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    farm_id uuid NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    lot_id uuid REFERENCES lots(id) ON DELETE CASCADE,
    collected_at timestamptz NOT NULL,
    collected int NOT NULL DEFAULT 0,
    broken int NOT NULL DEFAULT 0,
    downgraded int NOT NULL DEFAULT 0,
    sellable int NOT NULL DEFAULT 0
);

CREATE TABLE egg_stocks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    farm_id uuid NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    lot_id uuid REFERENCES lots(id) ON DELETE CASCADE,
    quantity int NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE egg_movements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    farm_id uuid NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    lot_id uuid REFERENCES lots(id) ON DELETE CASCADE,
    type varchar(20) NOT NULL,
    quantity int NOT NULL,
    occurred_at timestamptz NOT NULL,
    reason text
);
