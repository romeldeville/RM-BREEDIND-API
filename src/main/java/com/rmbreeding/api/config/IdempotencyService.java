package com.rmbreeding.api.config;

import com.rmbreeding.api.shared.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private final JdbcTemplate jdbcTemplate;
    private final IdempotencyProperties properties;

    public IdempotencyService(JdbcTemplate jdbcTemplate, IdempotencyProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public Optional<StoredResponse> find(String key, String requestHash) {
        return jdbcTemplate.query("""
                        select request_hash, status, response_body, response_content_type
                        from idempotency_keys
                        where tenant_id = ? and key = ? and expires_at > now()
                        """,
                rs -> rs.next()
                        ? Optional.of(new StoredResponse(
                                rs.getString("request_hash"),
                                rs.getInt("status"),
                                rs.getString("response_body"),
                                rs.getString("response_content_type")))
                        : Optional.empty(),
                tenantId(), key);
    }

    public void start(String key, String requestHash) {
        jdbcTemplate.update("""
                        insert into idempotency_keys
                            (id, tenant_id, key, request_hash, created_at, expires_at)
                        values (?, ?, ?, ?, now(), ?)
                        on conflict (tenant_id, key) do nothing
                        """,
                UUID.randomUUID(), tenantId(), key, requestHash,
                java.sql.Timestamp.from(Instant.now().plus(properties.getTtl())));
    }

    public void complete(String key, int status, String body, String contentType) {
        jdbcTemplate.update("""
                        update idempotency_keys
                        set status = ?, response_body = ?, response_content_type = ?
                        where tenant_id = ? and key = ?
                        """,
                status, body, contentType, tenantId(), key);
    }

    public static String hash(byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String hash(String body) {
        return hash(body.getBytes(StandardCharsets.UTF_8));
    }

    private UUID tenantId() {
        UUID tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : new UUID(0L, 0L);
    }

    public record StoredResponse(String requestHash, int status, String body, String contentType) {
    }
}
