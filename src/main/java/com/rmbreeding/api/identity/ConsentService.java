package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.RequestInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * P16-05 - consentement explicite à l'inscription, versions CGU et politique de confidentialité.
 */
@Service
public class ConsentService {

    public static final String TERMS = "TERMS_OF_SERVICE";
    public static final String PRIVACY = "PRIVACY_POLICY";

    private final JdbcTemplate jdbcTemplate;
    private final PrivacyProperties properties;

    public ConsentService(JdbcTemplate jdbcTemplate, PrivacyProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public void recordRegistrationConsent(UUID userId) {
        String ip = RequestInfo.clientIp();
        record(userId, TERMS, properties.getTermsVersion(), ip);
        record(userId, PRIVACY, properties.getPrivacyVersion(), ip);
    }

    public void record(UUID userId, String type, String version, String ip) {
        jdbcTemplate.update("""
                        INSERT INTO consents (id, user_id, type, version, accepted_at, ip)
                        VALUES (?, ?, ?, ?, now(), ?::inet)
                        """,
                UUID.randomUUID(), userId, type, version, ip);
    }

    public List<IdentityDtos.ConsentResponse> list(UUID userId) {
        return jdbcTemplate.query("""
                        SELECT type, version, accepted_at, host(ip) AS ip
                        FROM consents
                        WHERE user_id = ?
                        ORDER BY accepted_at DESC
                        """,
                (rs, rowNum) -> new IdentityDtos.ConsentResponse(
                        rs.getString("type"),
                        rs.getString("version"),
                        rs.getTimestamp("accepted_at").toInstant(),
                        rs.getString("ip")),
                userId);
    }

    public PrivacyProperties properties() {
        return properties;
    }
}
