CREATE TABLE species (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name varchar(120) NOT NULL,
    code varchar(40) NOT NULL,
    production_type varchar(20) NOT NULL,
    tracking_mode varchar(20) NOT NULL,
    gestation_days int,
    heat_cycle_days int,
    weaning_age_days int,
    incubation_days int,
    settings jsonb NOT NULL DEFAULT '{}'::jsonb,
    archived_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code)
);

CREATE TABLE species_templates (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    species_id uuid NOT NULL REFERENCES species(id) ON DELETE CASCADE,
    name varchar(120) NOT NULL,
    config jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE breeds (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    species_id uuid NOT NULL REFERENCES species(id) ON DELETE CASCADE,
    name varchar(120) NOT NULL,
    code varchar(40),
    archived_at timestamptz,
    UNIQUE (species_id, name)
);

CREATE TABLE vaccination_plans (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    species_id uuid NOT NULL REFERENCES species(id) ON DELETE CASCADE,
    product_id uuid,
    name varchar(120) NOT NULL,
    age_days int,
    booster_days int,
    schedule jsonb NOT NULL DEFAULT '[]'::jsonb,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE feeding_plans (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    species_id uuid NOT NULL REFERENCES species(id) ON DELETE CASCADE,
    name varchar(120) NOT NULL,
    stages jsonb NOT NULL DEFAULT '[]'::jsonb,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE growth_curves (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    species_id uuid NOT NULL REFERENCES species(id) ON DELETE CASCADE,
    breed_id uuid REFERENCES breeds(id) ON DELETE SET NULL,
    name varchar(120),
    points jsonb NOT NULL DEFAULT '[]'::jsonb
);

CREATE TABLE buildings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    farm_id uuid NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    name varchar(120) NOT NULL,
    code varchar(40),
    type varchar(40),
    archived_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE pens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    farm_id uuid NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    building_id uuid NOT NULL REFERENCES buildings(id) ON DELETE CASCADE,
    name varchar(120) NOT NULL,
    code varchar(40),
    type varchar(40),
    capacity int NOT NULL CHECK (capacity >= 0),
    archived_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE pen_movements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    farm_id uuid NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    animal_id uuid,
    lot_id uuid,
    from_pen_id uuid REFERENCES pens(id),
    to_pen_id uuid NOT NULL REFERENCES pens(id),
    moved_at timestamptz NOT NULL,
    reason text,
    actor_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (animal_id IS NOT NULL OR lot_id IS NOT NULL)
);

CREATE TRIGGER species_set_updated_at BEFORE UPDATE ON species
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER buildings_set_updated_at BEFORE UPDATE ON buildings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER pens_set_updated_at BEFORE UPDATE ON pens
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
