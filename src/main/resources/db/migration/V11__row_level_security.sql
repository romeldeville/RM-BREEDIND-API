DO $$
DECLARE
    t text;
    tables text[] := ARRAY[
        'memberships', 'farms', 'invitations', 'support_sessions',
        'species', 'species_templates', 'breeds', 'vaccination_plans', 'feeding_plans',
        'growth_curves', 'buildings', 'pens', 'pen_movements',
        'animals', 'animal_status_history', 'lots', 'lot_movements', 'lot_animals', 'lot_closures',
        'reproduction_cycles', 'heats', 'matings', 'pregnancy_diagnoses', 'abortions', 'births',
        'litters', 'weanings', 'health_products', 'vaccinations', 'treatments',
        'treatment_administrations', 'withdrawals', 'health_events', 'mortalities', 'quarantines',
        'feeds', 'feed_receipts', 'feed_distributions', 'feed_stocks', 'feed_costs', 'weighings',
        'egg_collections', 'egg_stocks', 'egg_movements',
        'customers', 'sales', 'sale_lines', 'payments', 'expenses', 'revenues', 'cost_allocations',
        'alerts', 'tasks', 'domain_events', 'sync_clients', 'sync_conflicts', 'attachments',
        'notification_log', 'notification_preferences', 'push_tokens', 'idempotency_keys',
        'subscriptions', 'invoices', 'payment_transactions', 'quota_usage'
    ];
BEGIN
    FOREACH t IN ARRAY tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_setting(''app.tenant_id'', true)::uuid) WITH CHECK (tenant_id = current_setting(''app.tenant_id'', true)::uuid)',
            t);
    END LOOP;
END
$$;
