package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.RequestInfo;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * P16-01 - append-only audit journal with before/after snapshots and request metadata.
 *
 * <p>The journal is written through JDBC (rather than JPA) so that {@code inet} and
 * {@code jsonb} columns are handled explicitly and so that audit entries survive even when
 * the surrounding entity is not managed.</p>
 */
@Service
public class AuditService {

    private static final int MAX_ACTION = 80;
    private static final int MAX_ENTITY_TYPE = 60;
    private static final int MAX_USER_AGENT = 500;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void record(String action, String entityType, UUID entityId) {
        record(action, entityType, entityId, null, null);
    }

    public void recordChange(String action, String entityType, UUID entityId,
            Map<String, Object> before, Map<String, Object> after) {
        record(action, entityType, entityId, before, after);
    }

    public void record(String action, String entityType, UUID entityId,
            Map<String, Object> before, Map<String, Object> after) {
        record(action, entityType, entityId, before, after, AuthContext.supportSessionId());
    }

    public void record(String action, String entityType, UUID entityId,
            Map<String, Object> before, Map<String, Object> after, UUID supportSessionId) {
        AuthPrincipal principal = AuthContext.get();
        UUID actorId = principal == null ? null : principal.userId();
        UUID sessionId = supportSessionId == null && principal != null
                ? principal.supportSessionId()
                : supportSessionId;
        jdbcTemplate.update("""
                        INSERT INTO audit_log
                            (id, tenant_id, actor_id, support_session_id, action, entity_type,
                             entity_id, before, after, ip, user_agent, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::inet, ?, now())
                        """,
                UUID.randomUUID(),
                TenantContext.getTenantId(),
                actorId,
                sessionId,
                truncate(action, MAX_ACTION),
                truncate(entityType, MAX_ENTITY_TYPE),
                entityId,
                toJson(before),
                toJson(after),
                RequestInfo.clientIp(),
                truncate(RequestInfo.userAgent(), MAX_USER_AGENT));
    }

    public List<AuditDtos.AuditEntryResponse> list(UUID tenantId, Instant from, Instant to,
            String entityType, UUID entityId, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, tenant_id, actor_id, support_session_id, action, entity_type, entity_id,
                       before, after, host(ip) AS ip, user_agent, occurred_at
                FROM audit_log
                WHERE tenant_id = ?
                """);
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenantId);
        if (from != null) {
            sql.append(" AND occurred_at >= ?");
            arguments.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND occurred_at <= ?");
            arguments.add(Timestamp.from(to));
        }
        if (entityType != null && !entityType.isBlank()) {
            sql.append(" AND entity_type = ?");
            arguments.add(entityType.trim());
        }
        if (entityId != null) {
            sql.append(" AND entity_id = ?");
            arguments.add(entityId);
        }
        sql.append(" ORDER BY occurred_at DESC LIMIT ?");
        arguments.add(Math.max(1, Math.min(limit, 200)));

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new AuditDtos.AuditEntryResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("actor_id", UUID.class),
                rs.getObject("support_session_id", UUID.class),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                fromJson(rs.getString("before")),
                fromJson(rs.getString("after")),
                rs.getString("ip"),
                rs.getString("user_agent"),
                rs.getTimestamp("occurred_at").toInstant()), arguments.toArray());
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            return "{\"error\":\"unserializable audit snapshot\"}";
        }
    }

    private Map<String, Object> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JacksonException ex) {
            return Map.of("raw", value);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
