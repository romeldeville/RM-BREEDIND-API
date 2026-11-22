CREATE TABLE reproduction_cycles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    farm_id uuid NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    female_id uuid NOT NULL REFERENCES animals(id) ON DELETE CASCADE,
    status varchar(30) NOT NULL,
    parity int NOT NULL DEFAULT 0,
    start_date date,
    end_date date,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE heats (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    cycle_id uuid NOT NULL REFERENCES reproduction_cycles(id) ON DELETE CASCADE,
    animal_id uuid NOT NULL REFERENCES animals(id) ON DELETE CASCADE,
    observed_at timestamptz NOT NULL,
    intensity varchar(20),
    next_expected_at date,
    notes text
);

CREATE TABLE matings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    cycle_id uuid NOT NULL REFERENCES reproduction_cycles(id) ON DELETE CASCADE,
    female_id uuid NOT NULL REFERENCES animals(id) ON DELETE CASCADE,
    male_id uuid REFERENCES animals(id),
    semen_code varchar(80),
    method varchar(20),
    mated_at timestamptz NOT NULL,
    operator_id uuid,
    reference_mating_id uuid,
    notes text
);

CREATE TABLE pregnancy_diagnoses (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    cycle_id uuid NOT NULL REFERENCES reproduction_cycles(id) ON DELETE CASCADE,
    diagnosed_at timestamptz NOT NULL,
    result varchar(20) NOT NULL,
    next_diagnosis_at date,
    notes text
);

CREATE TABLE abortions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    cycle_id uuid NOT NULL REFERENCES reproduction_cycles(id) ON DELETE CASCADE,
    occurred_at timestamptz NOT NULL,
    stage varchar(20),
    suspected_cause text
);

CREATE TABLE births (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    cycle_id uuid NOT NULL REFERENCES reproduction_cycles(id) ON DELETE CASCADE,
    female_id uuid NOT NULL REFERENCES animals(id) ON DELETE CASCADE,
    occurred_at timestamptz NOT NULL,
    born_alive int NOT NULL DEFAULT 0,
    stillborn int NOT NULL DEFAULT 0,
    mummified int NOT NULL DEFAULT 0,
    litter_weight numeric(10,2),
    assistance boolean NOT NULL DEFAULT false,
    complications text,
    operator_id uuid
);

CREATE TABLE litters (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    birth_id uuid NOT NULL REFERENCES births(id) ON DELETE CASCADE,
    lot_id uuid REFERENCES lots(id) ON DELETE SET NULL,
    name varchar(120),
    born_alive int NOT NULL DEFAULT 0,
    weaned_count int,
    weaning_date date
);

CREATE TABLE weanings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    litter_id uuid NOT NULL REFERENCES litters(id) ON DELETE CASCADE,
    weaned_at timestamptz NOT NULL,
    count int NOT NULL,
    average_weight numeric(10,2)
);

CREATE TABLE health_products (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name varchar(160) NOT NULL,
    type varchar(40),
    species_allowed text[],
    dose varchar(80),
    route varchar(40),
    withdrawal_meat_days int NOT NULL DEFAULT 0,
    withdrawal_eggs_days int NOT NULL DEFAULT 0,
    withdrawal_milk_days int NOT NULL DEFAULT 0,
    shelf_life_days int,
    supplier varchar(160),
    stock_qty numeric(12,2) NOT NULL DEFAULT 0,
    archived_at timestamptz
);

ALTER TABLE vaccination_plans
    ADD CONSTRAINT vaccination_plans_product_fk
    FOREIGN KEY (product_id) REFERENCES health_products(id) ON DELETE SET NULL;

CREATE TABLE vaccinations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    animal_id uuid REFERENCES animals(id) ON DELETE CASCADE,
    lot_id uuid REFERENCES lots(id) ON DELETE CASCADE,
    product_id uuid NOT NULL REFERENCES health_products(id),
    plan_id uuid REFERENCES vaccination_plans(id) ON DELETE SET NULL,
    vaccine_lot varchar(80),
    dose varchar(80),
    route varchar(40),
    count int NOT NULL DEFAULT 1,
    performed_at timestamptz NOT NULL,
    operator_id uuid,
    vet_id uuid,
    notes text,
    CHECK (animal_id IS NOT NULL OR lot_id IS NOT NULL)
);

CREATE TABLE treatments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    animal_id uuid REFERENCES animals(id) ON DELETE CASCADE,
    lot_id uuid REFERENCES lots(id) ON DELETE CASCADE,
    diagnosis varchar(200),
    symptoms text,
    product_id uuid NOT NULL REFERENCES health_products(id),
    dose varchar(80),
    route varchar(40),
    start_date date NOT NULL,
    duration_days int,
    end_date date,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    notes text,
    CHECK (animal_id IS NOT NULL OR lot_id IS NOT NULL)
);

CREATE TABLE treatment_administrations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    treatment_id uuid NOT NULL REFERENCES treatments(id) ON DELETE CASCADE,
    administered_at timestamptz NOT NULL,
    dose varchar(80),
    actor_id uuid
);

CREATE TABLE withdrawals (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    treatment_id uuid NOT NULL REFERENCES treatments(id) ON DELETE CASCADE,
    animal_id uuid REFERENCES animals(id) ON DELETE CASCADE,
    lot_id uuid REFERENCES lots(id) ON DELETE CASCADE,
    type varchar(20) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE health_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    animal_id uuid REFERENCES animals(id) ON DELETE CASCADE,
    lot_id uuid REFERENCES lots(id) ON DELETE CASCADE,
    symptoms text,
    affected_count int,
    diagnosis varchar(200),
    severity varchar(20),
    occurred_at timestamptz NOT NULL
);

CREATE TABLE mortalities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    animal_id uuid REFERENCES animals(id) ON DELETE CASCADE,
    lot_id uuid REFERENCES lots(id) ON DELETE CASCADE,
    count int NOT NULL DEFAULT 1,
    cause varchar(200),
    occurred_at timestamptz NOT NULL,
    actor_id uuid,
    CHECK (animal_id IS NOT NULL OR lot_id IS NOT NULL)
);

CREATE TABLE quarantines (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    animal_id uuid REFERENCES animals(id) ON DELETE CASCADE,
    lot_id uuid REFERENCES lots(id) ON DELETE CASCADE,
    start_date date NOT NULL,
    end_date date,
    reason text,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    released_by uuid,
    CHECK (animal_id IS NOT NULL OR lot_id IS NOT NULL)
);

CREATE TRIGGER reproduction_cycles_set_updated_at BEFORE UPDATE ON reproduction_cycles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
