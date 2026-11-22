CREATE INDEX idx_memberships_user ON memberships (user_id);
CREATE INDEX idx_farms_tenant ON farms (tenant_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id, expires_at);
CREATE INDEX idx_otp_codes_destination ON otp_codes (destination, created_at DESC);
CREATE INDEX idx_invitations_tenant ON invitations (tenant_id, expires_at);

CREATE INDEX idx_species_tenant ON species (tenant_id);
CREATE INDEX idx_breeds_species ON breeds (species_id);
CREATE INDEX idx_buildings_farm ON buildings (tenant_id, farm_id);
CREATE INDEX idx_pens_building ON pens (tenant_id, building_id);
CREATE INDEX idx_pen_movements_entity ON pen_movements (tenant_id, animal_id, lot_id);

CREATE INDEX idx_animals_farm_status ON animals (tenant_id, farm_id, status);
CREATE INDEX idx_animals_identifier_trgm ON animals USING gin (identifier gin_trgm_ops);
CREATE INDEX idx_animals_pen ON animals (tenant_id, pen_id);
CREATE INDEX idx_animal_status_history_animal ON animal_status_history (animal_id, changed_at DESC);
CREATE INDEX idx_lots_farm_status ON lots (tenant_id, farm_id, status);
CREATE INDEX idx_lot_movements_lot ON lot_movements (lot_id, occurred_at DESC);
CREATE INDEX idx_lot_animals_lot ON lot_animals (lot_id, animal_id);
CREATE INDEX idx_lot_animals_animal ON lot_animals (animal_id);

CREATE INDEX idx_cycles_female_status ON reproduction_cycles (tenant_id, female_id, status);
CREATE INDEX idx_heats_cycle ON heats (cycle_id, observed_at DESC);
CREATE INDEX idx_matings_cycle ON matings (cycle_id, mated_at DESC);
CREATE INDEX idx_diagnoses_cycle ON pregnancy_diagnoses (cycle_id, diagnosed_at DESC);
CREATE INDEX idx_births_cycle ON births (cycle_id, occurred_at DESC);
CREATE INDEX idx_litters_birth ON litters (birth_id);
CREATE INDEX idx_weanings_litter ON weanings (litter_id);

CREATE INDEX idx_vaccinations_animal ON vaccinations (tenant_id, animal_id, performed_at DESC);
CREATE INDEX idx_vaccinations_lot ON vaccinations (tenant_id, lot_id, performed_at DESC);
CREATE INDEX idx_treatments_status ON treatments (tenant_id, status, start_date DESC);
CREATE INDEX idx_treatment_admin_treatment ON treatment_administrations (treatment_id, administered_at DESC);
CREATE INDEX idx_withdrawals_active ON withdrawals (tenant_id, status, end_date);
CREATE INDEX idx_health_events_entity ON health_events (tenant_id, animal_id, lot_id);
CREATE INDEX idx_mortalities_entity ON mortalities (tenant_id, animal_id, lot_id, occurred_at DESC);
CREATE INDEX idx_quarantines_active ON quarantines (tenant_id, status, end_date);

CREATE INDEX idx_feed_receipts_feed ON feed_receipts (feed_id, received_at DESC);
CREATE INDEX idx_feed_distributions_entity ON feed_distributions (tenant_id, lot_id, animal_id, distributed_at DESC);
CREATE INDEX idx_feed_costs_lot ON feed_costs (tenant_id, lot_id, period_start);
CREATE INDEX idx_weighings_entity ON weighings (tenant_id, animal_id, lot_id, weighed_at DESC);
CREATE INDEX idx_egg_collections_lot ON egg_collections (tenant_id, lot_id, collected_at DESC);
CREATE INDEX idx_egg_movements_lot ON egg_movements (tenant_id, lot_id, occurred_at DESC);

CREATE INDEX idx_customers_farm ON customers (tenant_id, farm_id);
CREATE INDEX idx_sales_farm_date ON sales (tenant_id, farm_id, sold_at DESC);
CREATE INDEX idx_sales_customer ON sales (tenant_id, customer_id);
CREATE INDEX idx_sale_lines_sale ON sale_lines (sale_id);
CREATE INDEX idx_payments_sale ON payments (sale_id, paid_at DESC);
CREATE INDEX idx_expenses_farm_date ON expenses (tenant_id, farm_id, incurred_at DESC);
CREATE INDEX idx_revenues_farm_date ON revenues (tenant_id, farm_id, received_at DESC);
CREATE INDEX idx_cost_allocations_lot ON cost_allocations (tenant_id, lot_id, period);

CREATE INDEX idx_alerts_open ON alerts (tenant_id, farm_id, status, due_date);
CREATE INDEX idx_tasks_open ON tasks (tenant_id, farm_id, status, due_date);
CREATE INDEX idx_audit_entity ON audit_log (tenant_id, entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_domain_events_seq ON domain_events (tenant_id, server_seq);
CREATE INDEX idx_domain_events_aggregate ON domain_events (tenant_id, aggregate_type, aggregate_id, occurred_at DESC);
CREATE INDEX idx_sync_conflicts_tenant ON sync_conflicts (tenant_id, resolved_at);
CREATE INDEX idx_attachments_entity ON attachments (tenant_id, entity_type, entity_id);
CREATE INDEX idx_notification_log_tenant ON notification_log (tenant_id, sent_at DESC);
CREATE INDEX idx_push_tokens_user ON push_tokens (tenant_id, user_id);
CREATE INDEX idx_idempotency_expiry ON idempotency_keys (expires_at);

CREATE INDEX idx_subscriptions_tenant ON subscriptions (tenant_id);
CREATE INDEX idx_invoices_tenant ON invoices (tenant_id, issued_at DESC);
CREATE INDEX idx_payment_transactions_tenant ON payment_transactions (tenant_id, created_at DESC);
