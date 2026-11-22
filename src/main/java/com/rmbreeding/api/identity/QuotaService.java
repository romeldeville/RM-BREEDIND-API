package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QuotaService {

    private final JdbcTemplate jdbcTemplate;

    public QuotaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<IdentityDtos.QuotaResponse> quotas(UUID tenantId) {
        return jdbcTemplate.query("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                SELECT qu.quota_key, qu.used, COALESCE(qu.limit_value, (p.quotas->>qu.quota_key)::int) AS lim
                FROM quota_usage qu
                LEFT JOIN subscriptions s ON s.tenant_id = qu.tenant_id
                LEFT JOIN plans p ON p.id = s.plan_id
                CROSS JOIN ctx
                WHERE qu.tenant_id = ?
                ORDER BY qu.quota_key
                """, (rs, rowNum) -> new IdentityDtos.QuotaResponse(
                rs.getString("quota_key"), rs.getInt("used"),
                (Integer) rs.getObject("lim")), tenantId.toString(), tenantId);
    }

    public Integer limit(UUID tenantId, String quotaKey) {
        List<Integer> values = jdbcTemplate.query("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                SELECT COALESCE(qu.limit_value, (p.quotas->>qu.quota_key)::int) AS lim
                FROM quota_usage qu
                LEFT JOIN subscriptions s ON s.tenant_id = qu.tenant_id
                LEFT JOIN plans p ON p.id = s.plan_id
                CROSS JOIN ctx
                WHERE qu.tenant_id = ? AND qu.quota_key = ?
                """, (rs, rowNum) -> (Integer) rs.getObject("lim"), tenantId.toString(), tenantId, quotaKey);
        return values.isEmpty() ? null : values.getFirst();
    }

    public void requireCapacity(UUID tenantId, String quotaKey, long currentCount) {
        Integer max = limit(tenantId, quotaKey);
        if (max != null && currentCount >= max) {
            throw new BusinessRuleException(ErrorCode.QUOTA_EXCEEDED,
                    "Quota exceeded for " + quotaKey + " (limit " + max + ")");
        }
    }

    public void setUsed(UUID tenantId, String quotaKey, int used) {
        jdbcTemplate.update("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                UPDATE quota_usage SET used = ?, updated_at = now()
                FROM ctx WHERE quota_usage.tenant_id = ? AND quota_key = ?
                """, tenantId.toString(), used, tenantId, quotaKey);
    }

    public void provision(UUID tenantId, String planCode) {
        jdbcTemplate.update("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                INSERT INTO subscriptions (tenant_id, plan_id, provider, status, current_period_start,
                    current_period_end, trial_ends_at)
                SELECT ?, p.id, 'NONE', 'ACTIVE', now(), now() + interval '1 month',
                    now() + make_interval(days => p.trial_days)
                FROM plans p CROSS JOIN ctx WHERE p.code = ?
                """, tenantId.toString(), tenantId, planCode);
        jdbcTemplate.update("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                INSERT INTO quota_usage (tenant_id, quota_key, used, limit_value)
                SELECT ?, q.key, 0, (q.value)::int
                FROM plans p, jsonb_each_text(p.quotas) AS q(key, value), ctx
                WHERE p.code = ?
                ON CONFLICT (tenant_id, quota_key) DO NOTHING
                """, tenantId.toString(), tenantId, planCode);
    }
}
