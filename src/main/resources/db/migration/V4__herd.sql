CREATE TABLE animals (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    farm_id uuid NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    species_id uuid NOT NULL REFERENCES species(id),
    breed_id uuid REFERENCES breeds(id),
    pen_id uuid REFERENCES pens(id),
    identifier varchar(80) NOT NULL,
    name varchar(120),
    sex varchar(10) NOT NULL,
    birth_date date,
    birth_estimated boolean NOT NULL DEFAULT false,
    origin varchar(40),
    status varchar(30) NOT NULL DEFAULT 'ACTIVE',
    mother_id uuid,
    father_id uuid,
    photo_key text,
    weight_kg numeric(10,2),
    acquired_at date,
    version int NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    archived_at timestamptz,
    UNIQUE (tenant_id, farm_id, identifier)
);

ALTER TABLE animals
    ADD CONSTRAINT animals_mother_fk FOREIGN KEY (mother_id) REFERENCES animals(id),
    ADD CONSTRAINT animals_father_fk FOREIGN KEY (father_id) REFERENCES animals(id);

CREATE TABLE animal_status_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    animal_id uuid NOT NULL REFERENCES animals(id) ON DELETE CASCADE,
    status varchar(30) NOT NULL,
    changed_at timestamptz NOT NULL,
    reason text,
    actor_id uuid
);

CREATE TABLE lots (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    farm_id uuid NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    species_id uuid NOT NULL REFERENCES species(id),
    name varchar(120) NOT NULL,
    code varchar(40),
    production_type varchar(20),
    status varchar(20) NOT NULL DEFAULT 'OPEN',
    entry_date date,
    initial_count int NOT NULL DEFAULT 0,
    current_count int NOT NULL DEFAULT 0 CHECK (current_count >= 0),
    origin varchar(40),
    unit_cost numeric(14,2),
    closed_at timestamptz,
    version int NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE lot_movements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    lot_id uuid NOT NULL REFERENCES lots(id) ON DELETE CASCADE,
    type varchar(30) NOT NULL,
    quantity int NOT NULL CHECK (quantity > 0),
    occurred_at timestamptz NOT NULL,
    reason text,
    actor_id uuid,
    source_event_id uuid,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE lot_animals (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    lot_id uuid NOT NULL REFERENCES lots(id) ON DELETE CASCADE,
    animal_id uuid NOT NULL REFERENCES animals(id) ON DELETE CASCADE,
    joined_at timestamptz NOT NULL,
    left_at timestamptz
);

CREATE TABLE lot_closures (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    lot_id uuid NOT NULL UNIQUE REFERENCES lots(id) ON DELETE CASCADE,
    mortality_rate numeric(8,4),
    feed_conversion numeric(10,4),
    gmq numeric(10,4),
    cost numeric(14,2),
    revenue numeric(14,2),
    margin numeric(14,2),
    closed_at timestamptz NOT NULL
);

CREATE TRIGGER animals_set_updated_at BEFORE UPDATE ON animals
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER lots_set_updated_at BEFORE UPDATE ON lots
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
