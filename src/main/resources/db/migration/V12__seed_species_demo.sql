INSERT INTO plans (id, code, name, price_cents, currency, interval, trial_days, quotas, active) VALUES
    ('10000000-0000-0000-0000-000000000001', 'FREE', 'Gratuit', 0, 'XOF', 'MONTH', 0,
     '{"animals": 50, "farms": 1, "members": 2, "storageMb": 200}'::jsonb, true),
    ('10000000-0000-0000-0000-000000000002', 'STARTER', 'Starter', 500000, 'XOF', 'MONTH', 14,
     '{"animals": 300, "farms": 2, "members": 5, "storageMb": 2048}'::jsonb, true),
    ('10000000-0000-0000-0000-000000000003', 'PRO', 'Pro', 1500000, 'XOF', 'MONTH', 14,
     '{"animals": 2000, "farms": 5, "members": 15, "storageMb": 10240}'::jsonb, true),
    ('10000000-0000-0000-0000-000000000004', 'BUSINESS', 'Business', 3500000, 'XOF', 'MONTH', 14,
     '{"animals": 10000, "farms": 20, "members": 50, "storageMb": 51200}'::jsonb, true);

INSERT INTO tenants (id, name, slug, status, plan_code) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Ferme Demo', 'demo', 'ACTIVE', 'PRO'),
    ('22222222-2222-2222-2222-222222222222', 'Ferme Demo 2', 'demo2', 'ACTIVE', 'STARTER');

INSERT INTO users (id, email, phone, status, locale, platform_role) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'demo@rmbreeding.local', '+22500000001', 'ACTIVE', 'fr', NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'demo2@rmbreeding.local', '+22500000002', 'ACTIVE', 'fr', NULL),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'admin@rmbreeding.local', '+22500000003', 'ACTIVE', 'fr', 'PLATFORM_ADMIN');

SET LOCAL app.tenant_id = '11111111-1111-1111-1111-111111111111';

INSERT INTO memberships (id, tenant_id, user_id, role, farm_scope) VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', '11111111-1111-1111-1111-111111111111',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'OWNER', '{}');

INSERT INTO farms (id, tenant_id, name, code, country, currency, timezone, address) VALUES
    ('33333333-3333-3333-3333-333333333331', '11111111-1111-1111-1111-111111111111',
     'Ferme Demo Principale', 'DEMO1', 'CI', 'XOF', 'Africa/Abidjan', 'Abidjan, Cote d''Ivoire');

INSERT INTO species (id, tenant_id, name, code, production_type, tracking_mode, gestation_days, heat_cycle_days, weaning_age_days, incubation_days) VALUES
    ('44444444-4444-4444-4444-444444444401', '11111111-1111-1111-1111-111111111111', 'Volaille', 'VOLAILLE', 'EGGS', 'LOT', NULL, NULL, 42, 21),
    ('44444444-4444-4444-4444-444444444402', '11111111-1111-1111-1111-111111111111', 'Bovin', 'BOVIN', 'MILK', 'INDIVIDUAL', 283, 21, 60, NULL),
    ('44444444-4444-4444-4444-444444444403', '11111111-1111-1111-1111-111111111111', 'Caprin', 'CAPRIN', 'MILK', 'INDIVIDUAL', 150, 21, 60, NULL),
    ('44444444-4444-4444-4444-444444444404', '11111111-1111-1111-1111-111111111111', 'Ovin', 'OVIN', 'MEAT', 'INDIVIDUAL', 147, 17, 60, NULL),
    ('44444444-4444-4444-4444-444444444405', '11111111-1111-1111-1111-111111111111', 'Porcin', 'PORCIN', 'MEAT', 'INDIVIDUAL', 114, 21, 28, NULL),
    ('44444444-4444-4444-4444-444444444406', '11111111-1111-1111-1111-111111111111', 'Lapin', 'LAPIN', 'MEAT', 'INDIVIDUAL', 31, 16, 35, NULL);

INSERT INTO breeds (id, tenant_id, species_id, name, code) VALUES
    ('55555555-5555-5555-5555-555555555501', '11111111-1111-1111-1111-111111111111', '44444444-4444-4444-4444-444444444401', 'Poulet de chair', 'CHAIR'),
    ('55555555-5555-5555-5555-555555555502', '11111111-1111-1111-1111-111111111111', '44444444-4444-4444-4444-444444444401', 'Poule pondeuse', 'PONTE'),
    ('55555555-5555-5555-5555-555555555503', '11111111-1111-1111-1111-111111111111', '44444444-4444-4444-4444-444444444402', 'Zebu', 'ZEBU'),
    ('55555555-5555-5555-5555-555555555504', '11111111-1111-1111-1111-111111111111', '44444444-4444-4444-4444-444444444403', 'Djallonke', 'DJALLONKE'),
    ('55555555-5555-5555-5555-555555555505', '11111111-1111-1111-1111-111111111111', '44444444-4444-4444-4444-444444444404', 'Djallonke', 'DJALLONKE'),
    ('55555555-5555-5555-5555-555555555506', '11111111-1111-1111-1111-111111111111', '44444444-4444-4444-4444-444444444405', 'Large White', 'LARGE_WHITE'),
    ('55555555-5555-5555-5555-555555555507', '11111111-1111-1111-1111-111111111111', '44444444-4444-4444-4444-444444444406', 'Néo-Zélandais', 'NZ');

SET LOCAL app.tenant_id = '22222222-2222-2222-2222-222222222222';

INSERT INTO memberships (id, tenant_id, user_id, role, farm_scope) VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2', '22222222-2222-2222-2222-222222222222',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'OWNER', '{}');

INSERT INTO farms (id, tenant_id, name, code, country, currency, timezone, address) VALUES
    ('33333333-3333-3333-3333-333333333332', '22222222-2222-2222-2222-222222222222',
     'Ferme Demo 2', 'DEMO2', 'CI', 'XOF', 'Africa/Abidjan', 'Bouake, Cote d''Ivoire');

INSERT INTO species (id, tenant_id, name, code, production_type, tracking_mode, gestation_days, heat_cycle_days, weaning_age_days, incubation_days) VALUES
    ('44444444-4444-4444-4444-444444444411', '22222222-2222-2222-2222-222222222222', 'Volaille', 'VOLAILLE', 'EGGS', 'LOT', NULL, NULL, 42, 21),
    ('44444444-4444-4444-4444-444444444412', '22222222-2222-2222-2222-222222222222', 'Bovin', 'BOVIN', 'MILK', 'INDIVIDUAL', 283, 21, 60, NULL);

INSERT INTO breeds (id, tenant_id, species_id, name, code) VALUES
    ('55555555-5555-5555-5555-555555555511', '22222222-2222-2222-2222-222222222222', '44444444-4444-4444-4444-444444444411', 'Poule pondeuse', 'PONTE'),
    ('55555555-5555-5555-5555-555555555512', '22222222-2222-2222-2222-222222222222', '44444444-4444-4444-4444-444444444412', 'Zebu', 'ZEBU');
