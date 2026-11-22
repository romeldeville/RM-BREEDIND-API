package com.rmbreeding.api.billing;

import com.rmbreeding.api.billing.BillingDtos.*;
import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public BillingService(JdbcTemplate jdbcTemplate, AuditService auditService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<PlanDto> listPlans() {
        return jdbcTemplate.query("""
                SELECT id, code, name, price_cents, currency, interval, trial_days, quotas, active
                FROM plans
                WHERE active = true
                ORDER BY price_cents ASC
                """, (rs, rowNum) -> {
            Map<String, Object> quotasMap = Collections.emptyMap();
            String quotasJson = rs.getString("quotas");
            if (quotasJson != null && !quotasJson.isBlank()) {
                try {
                    quotasMap = objectMapper.readValue(quotasJson, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    log.warn("Failed to parse quotas JSON for plan: {}", rs.getString("code"));
                }
            }
            return new PlanDto(
                    (UUID) rs.getObject("id"),
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getInt("price_cents"),
                    rs.getString("currency").trim(),
                    rs.getString("interval"),
                    rs.getInt("trial_days"),
                    quotasMap,
                    rs.getBoolean("active")
            );
        });
    }

    public Optional<SubscriptionDto> getSubscription(UUID tenantId) {
        List<SubscriptionDto> list = jdbcTemplate.query("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                SELECT s.id, s.tenant_id, p.code AS plan_code, p.name AS plan_name, s.provider,
                       s.status, s.current_period_start, s.current_period_end, s.trial_ends_at
                FROM subscriptions s
                JOIN plans p ON p.id = s.plan_id
                CROSS JOIN ctx
                WHERE s.tenant_id = ?
                """, (rs, rowNum) -> {
            Timestamp trialEnds = rs.getTimestamp("trial_ends_at");
            Instant trialInst = trialEnds != null ? trialEnds.toInstant() : null;
            boolean isTrialActive = trialInst != null && trialInst.isAfter(Instant.now());
            Timestamp periodStart = rs.getTimestamp("current_period_start");
            Timestamp periodEnd = rs.getTimestamp("current_period_end");

            return new SubscriptionDto(
                    (UUID) rs.getObject("id"),
                    (UUID) rs.getObject("tenant_id"),
                    rs.getString("plan_code"),
                    rs.getString("plan_name"),
                    rs.getString("provider"),
                    rs.getString("status"),
                    periodStart != null ? periodStart.toInstant() : null,
                    periodEnd != null ? periodEnd.toInstant() : null,
                    trialInst,
                    isTrialActive
            );
        }, tenantId.toString(), tenantId);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public CheckoutResponse createCheckoutSession(UUID tenantId, CheckoutRequest request) {
        String sessionId = "cs_test_" + UUID.randomUUID();
        String checkoutUrl = "https://checkout.stripe.com/pay/" + sessionId;
        return new CheckoutResponse(checkoutUrl, sessionId);
    }

    public CustomerPortalResponse createPortalSession(UUID tenantId) {
        String portalUrl = "https://billing.stripe.com/p/session/test_" + UUID.randomUUID();
        return new CustomerPortalResponse(portalUrl);
    }

    @Transactional
    public void changePlan(UUID tenantId, String newPlanCode) {
        int updated = jdbcTemplate.update("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                UPDATE subscriptions
                SET plan_id = p.id, updated_at = now()
                FROM plans p, ctx
                WHERE subscriptions.tenant_id = ? AND p.code = ?
                """, tenantId.toString(), tenantId, newPlanCode);

        if (updated == 0) {
            throw new BusinessRuleException(ErrorCode.NOT_FOUND, "Plan or subscription not found");
        }

        // Update quota_usage limits to match the new plan
        jdbcTemplate.update("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                INSERT INTO quota_usage (tenant_id, quota_key, used, limit_value)
                SELECT ?, q.key, 0, (q.value)::int
                FROM plans p, jsonb_each_text(p.quotas) AS q(key, value), ctx
                WHERE p.code = ?
                ON CONFLICT (tenant_id, quota_key) DO UPDATE
                SET limit_value = EXCLUDED.limit_value, updated_at = now()
                """, tenantId.toString(), tenantId, newPlanCode);

        auditService.record("CHANGE_PLAN", "SUBSCRIPTION", tenantId, null,
                Map.of("newPlanCode", newPlanCode));
    }

    @Transactional
    public void cancelSubscription(UUID tenantId) {
        jdbcTemplate.update("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                UPDATE subscriptions
                SET status = 'CANCELLED', cancel_at = now(), updated_at = now()
                FROM ctx
                WHERE subscriptions.tenant_id = ?
                """, tenantId.toString(), tenantId);

        auditService.record("CANCEL_SUBSCRIPTION", "SUBSCRIPTION", tenantId);
    }

    @Transactional
    public void reactivateSubscription(UUID tenantId) {
        jdbcTemplate.update("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                UPDATE subscriptions
                SET status = 'ACTIVE', cancel_at = null, updated_at = now()
                FROM ctx
                WHERE subscriptions.tenant_id = ?
                """, tenantId.toString(), tenantId);

        auditService.record("REACTIVATE_SUBSCRIPTION", "SUBSCRIPTION", tenantId);
    }

    public List<InvoiceDto> listInvoices(UUID tenantId) {
        return jdbcTemplate.query("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                SELECT id, number, amount_cents, currency, status, issued_at, paid_at, pdf_key
                FROM invoices
                CROSS JOIN ctx
                WHERE tenant_id = ?
                ORDER BY issued_at DESC
                """, (rs, rowNum) -> {
            Timestamp issued = rs.getTimestamp("issued_at");
            Timestamp paid = rs.getTimestamp("paid_at");
            return new InvoiceDto(
                    (UUID) rs.getObject("id"),
                    rs.getString("number"),
                    rs.getInt("amount_cents"),
                    rs.getString("currency").trim(),
                    rs.getString("status"),
                    issued != null ? issued.toInstant() : null,
                    paid != null ? paid.toInstant() : null,
                    rs.getString("pdf_key")
            );
        }, tenantId.toString(), tenantId);
    }

    @Transactional
    public MobileMoneyPaymentResponse initiateMobileMoney(UUID tenantId, MobileMoneyPaymentRequest request) {
        String ref = "MOM-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000);
        UUID txId = UUID.randomUUID();

        jdbcTemplate.update("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                INSERT INTO payment_transactions (id, tenant_id, provider, provider_ref, amount_cents, currency, status)
                SELECT ?, ?, ?, ?, ?, 'XOF', 'PENDING'
                FROM ctx
                """, tenantId.toString(), txId, tenantId, request.provider(), ref, request.amountCents());

        String instructions = "Validez le paiement de " + (request.amountCents() / 100) + " XOF sur votre téléphone " + request.phoneNumber();
        return new MobileMoneyPaymentResponse(txId.toString(), "PENDING", ref, instructions);
    }

    @Transactional
    public boolean processWebhook(String provider, String eventId, String eventType, String payloadJson) {
        // Idempotency check: insert event
        int inserted = jdbcTemplate.update("""
                INSERT INTO billing_events (provider, event_id, type, payload, processed_at)
                VALUES (?, ?, ?, ?::jsonb, now())
                ON CONFLICT (event_id) DO NOTHING
                """, provider, eventId, eventType, payloadJson);

        if (inserted == 0) {
            log.info("Webhook event already processed (idempotent ignore): {} / {}", provider, eventId);
            return false;
        }

        log.info("Processing webhook: provider={}, eventId={}, type={}", provider, eventId, eventType);
        return true;
    }
}
