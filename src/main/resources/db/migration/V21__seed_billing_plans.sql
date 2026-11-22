INSERT INTO plans (code, name, price_cents, currency, interval, trial_days, quotas, active)
VALUES
    ('TRIAL', 'Essai Gratuit', 0, 'XOF', 'MONTH', 14, '{"maxFarms": 1, "maxAnimals": 100, "maxUsers": 3, "storageGb": 1}'::jsonb, true),
    ('STARTER', 'Starter', 990000, 'XOF', 'MONTH', 0, '{"maxFarms": 1, "maxAnimals": 500, "maxUsers": 5, "storageGb": 5}'::jsonb, true),
    ('PRO', 'Professionnel', 2490000, 'XOF', 'MONTH', 0, '{"maxFarms": 5, "maxAnimals": 5000, "maxUsers": 20, "storageGb": 50}'::jsonb, true),
    ('ENTERPRISE', 'Entreprise', 0, 'XOF', 'MONTH', 0, '{"maxFarms": -1, "maxAnimals": -1, "maxUsers": -1, "storageGb": 500}'::jsonb, true)
ON CONFLICT (code) DO NOTHING;
