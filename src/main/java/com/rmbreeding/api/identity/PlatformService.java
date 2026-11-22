package com.rmbreeding.api.identity;

import com.rmbreeding.api.identity.PlatformDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    public PlatformService(JdbcTemplate jdbcTemplate, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    public List<PlatformTenantDto> listTenants(String search) {
        String filter = search == null || search.isBlank() ? "%" : "%" + search.trim() + "%";
        return jdbcTemplate.query("""
                SELECT t.id, t.name, t.slug, t.status, COALESCE(p.code, 'TRIAL') AS plan_code,
                       (SELECT count(*) FROM farms f WHERE f.tenant_id = t.id) AS farms_count,
                       (SELECT count(*) FROM animals a WHERE a.tenant_id = t.id) AS animals_count,
                       t.created_at
                FROM tenants t
                LEFT JOIN subscriptions s ON s.tenant_id = t.id
                LEFT JOIN plans p ON p.id = s.plan_id
                WHERE t.name ILIKE ? OR t.slug ILIKE ?
                ORDER BY t.created_at DESC
                """, (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("created_at");
            return new PlatformTenantDto(
                    (UUID) rs.getObject("id"),
                    rs.getString("name"),
                    rs.getString("slug"),
                    rs.getString("status"),
                    rs.getString("plan_code"),
                    rs.getInt("farms_count"),
                    rs.getInt("animals_count"),
                    ts != null ? ts.toInstant() : Instant.now()
            );
        }, filter, filter);
    }

    public PlatformTenantDto getTenantDetail(UUID tenantId) {
        List<PlatformTenantDto> list = jdbcTemplate.query("""
                SELECT t.id, t.name, t.slug, t.status, COALESCE(p.code, 'TRIAL') AS plan_code,
                       (SELECT count(*) FROM farms f WHERE f.tenant_id = t.id) AS farms_count,
                       (SELECT count(*) FROM animals a WHERE a.tenant_id = t.id) AS animals_count,
                       t.created_at
                FROM tenants t
                LEFT JOIN subscriptions s ON s.tenant_id = t.id
                LEFT JOIN plans p ON p.id = s.plan_id
                WHERE t.id = ?
                """, (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("created_at");
            return new PlatformTenantDto(
                    (UUID) rs.getObject("id"),
                    rs.getString("name"),
                    rs.getString("slug"),
                    rs.getString("status"),
                    rs.getString("plan_code"),
                    rs.getInt("farms_count"),
                    rs.getInt("animals_count"),
                    ts != null ? ts.toInstant() : Instant.now()
            );
        }, tenantId);

        return list.isEmpty() ? null : list.getFirst();
    }

    @Transactional
    public void suspendTenant(UUID tenantId) {
        jdbcTemplate.update("UPDATE tenants SET status = 'SUSPENDED', updated_at = now() WHERE id = ?", tenantId);
        auditService.record("PLATFORM_SUSPEND_TENANT", "TENANT", tenantId);
    }

    @Transactional
    public void reactivateTenant(UUID tenantId) {
        jdbcTemplate.update("UPDATE tenants SET status = 'ACTIVE', updated_at = now() WHERE id = ?", tenantId);
        auditService.record("PLATFORM_REACTIVATE_TENANT", "TENANT", tenantId);
    }

    public PlatformMetricsDto getMetrics() {
        Long totalTenants = jdbcTemplate.queryForObject("SELECT count(*) FROM tenants", Long.class);
        Long activeTenants = jdbcTemplate.queryForObject("SELECT count(*) FROM tenants WHERE status = 'ACTIVE'", Long.class);
        Long totalFarms = jdbcTemplate.queryForObject("SELECT count(*) FROM farms", Long.class);
        Long totalAnimals = jdbcTemplate.queryForObject("SELECT count(*) FROM animals", Long.class);
        Long totalUsers = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class);
        Long mrr = jdbcTemplate.queryForObject("""
                SELECT COALESCE(sum(p.price_cents), 0)
                FROM subscriptions s
                JOIN plans p ON p.id = s.plan_id
                WHERE s.status = 'ACTIVE'
                """, Long.class);

        return new PlatformMetricsDto(
                totalTenants != null ? totalTenants : 0,
                activeTenants != null ? activeTenants : 0,
                totalFarms != null ? totalFarms : 0,
                totalAnimals != null ? totalAnimals : 0,
                totalUsers != null ? totalUsers : 0,
                mrr != null ? mrr : 0
        );
    }

    public List<AnnouncementDto> listAnnouncements() {
        return jdbcTemplate.query("""
                SELECT id, title, message, severity, starts_at, ends_at, active
                FROM announcements
                WHERE active = true
                ORDER BY starts_at DESC
                """, (rs, rowNum) -> {
            Timestamp start = rs.getTimestamp("starts_at");
            Timestamp end = rs.getTimestamp("ends_at");
            return new AnnouncementDto(
                    (UUID) rs.getObject("id"),
                    rs.getString("title"),
                    rs.getString("message"),
                    rs.getString("severity"),
                    start != null ? start.toInstant() : Instant.now(),
                    end != null ? end.toInstant() : null,
                    rs.getBoolean("active")
            );
        });
    }

    @Transactional
    public AnnouncementDto createAnnouncement(CreateAnnouncementRequest req) {
        UUID id = UUID.randomUUID();
        Timestamp start = Timestamp.from(req.startsAt() != null ? req.startsAt() : Instant.now());
        Timestamp end = req.endsAt() != null ? Timestamp.from(req.endsAt()) : null;

        jdbcTemplate.update("""
                INSERT INTO announcements (id, title, message, severity, starts_at, ends_at, active)
                VALUES (?, ?, ?, ?, ?, ?, true)
                """, id, req.title(), req.message(), req.severity(), start, end);

        auditService.record("CREATE_ANNOUNCEMENT", "ANNOUNCEMENT", id);
        return new AnnouncementDto(id, req.title(), req.message(), req.severity(),
                req.startsAt() != null ? req.startsAt() : Instant.now(), req.endsAt(), true);
    }

    public String exportTenantsCsv() {
        List<PlatformTenantDto> tenants = listTenants("");
        StringBuilder sb = new StringBuilder("id,name,slug,status,planCode,farmsCount,animalsCount,createdAt\n");
        for (PlatformTenantDto t : tenants) {
            sb.append(t.id()).append(",")
              .append("\"").append(t.name().replace("\"", "\"\"")).append("\",")
              .append(t.slug()).append(",")
              .append(t.status()).append(",")
              .append(t.planCode()).append(",")
              .append(t.farmsCount()).append(",")
              .append(t.animalsCount()).append(",")
              .append(t.createdAt()).append("\n");
        }
        return sb.toString();
    }
}
