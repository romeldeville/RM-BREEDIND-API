package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;
    private final SecretKey previousKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        String previous = properties.getPreviousSecret();
        this.previousKey = previous == null || previous.isBlank() ? null
                : Keys.hmacShaKeyFor(previous.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(UUID userId, UUID tenantId, List<String> roles, boolean platformAdmin,
            UUID supportSessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(userId.toString())
                .claim("tenantId", tenantId == null ? null : tenantId.toString())
                .claim("roles", roles)
                .claim("platformAdmin", platformAdmin)
                .claim("supportSessionId", supportSessionId == null ? null : supportSessionId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTokenTtl())))
                .signWith(key)
                .compact();
    }

    public AuthPrincipal parse(String token) throws JwtException {
        try {
            return principal(parseClaims(token, key));
        } catch (JwtException ex) {
            if (previousKey == null) {
                throw ex;
            }
            return principal(parseClaims(token, previousKey));
        }
    }

    private Claims parseClaims(String token, SecretKey verificationKey) {
        return Jwts.parser()
                .verifyWith(verificationKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static AuthPrincipal principal(Claims claims) {
        List<?> rawRoles = claims.get("roles", List.class);
        List<String> roles = rawRoles == null ? List.of()
                : rawRoles.stream().map(String::valueOf).toList();
        return new AuthPrincipal(
                UUID.fromString(claims.getSubject()),
                uuid(claims.get("tenantId", String.class)),
                roles,
                Boolean.TRUE.equals(claims.get("platformAdmin", Boolean.class)),
                uuid(claims.get("supportSessionId", String.class)));
    }

    public long accessTokenTtlSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }

    private static UUID uuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
