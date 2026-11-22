CREATE TABLE customers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    farm_id uuid NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    name varchar(160) NOT NULL,
    phone varchar(32),
    email citext,
    address text,
    balance numeric(14,2) NOT NULL DEFAULT 0,
    archived_at timestamptz
);

CREATE TABLE sales (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    farm_id uuid NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    customer_id uuid REFERENCES customers(id) ON DELETE SET NULL,
    number varchar(40) NOT NULL,
    type varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    sold_at timestamptz NOT NULL,
    total_amount numeric(14,2) NOT NULL DEFAULT 0,
    currency char(3) NOT NULL,
    notes text,
    cancellation_deadline timestamptz,
    UNIQUE (tenant_id, number)
);

CREATE TABLE sale_lines (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sale_id uuid NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    item_type varchar(20) NOT NULL,
    animal_id uuid REFERENCES animals(id) ON DELETE SET NULL,
    lot_id uuid REFERENCES lots(id) ON DELETE SET NULL,
    description varchar(200),
    quantity numeric(12,3) NOT NULL,
    unit_price numeric(14,2) NOT NULL,
    amount numeric(14,2) NOT NULL
);

CREATE TABLE payments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sale_id uuid NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    amount numeric(14,2) NOT NULL CHECK (amount >= 0),
    method varchar(30) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    paid_at timestamptz,
    reference varchar(120)
);

CREATE TABLE expenses (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    farm_id uuid NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    category varchar(60) NOT NULL,
    amount numeric(14,2) NOT NULL CHECK (amount > 0),
    currency char(3) NOT NULL,
    incurred_at date NOT NULL,
    description text,
    supplier varchar(160),
    attachment_id uuid
);

CREATE TABLE revenues (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    farm_id uuid NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    category varchar(60) NOT NULL,
    amount numeric(14,2) NOT NULL CHECK (amount > 0),
    currency char(3) NOT NULL,
    received_at date NOT NULL,
    description text,
    sale_id uuid REFERENCES sales(id) ON DELETE SET NULL
);

CREATE TABLE cost_allocations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    lot_id uuid NOT NULL REFERENCES lots(id) ON DELETE CASCADE,
    source_type varchar(30) NOT NULL,
    source_id uuid,
    period varchar(7),
    amount numeric(14,2) NOT NULL,
    basis varchar(40),
    allocated_at timestamptz NOT NULL
);
