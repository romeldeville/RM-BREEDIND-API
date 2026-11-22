package com.rmbreeding.api.identity;

import com.rmbreeding.api.shared.JwtProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService service(Duration accessTtl) {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("rm-breeding-test");
        properties.setSecret("test-secret-with-at-least-thirty-two-bytes!!");
        properties.setAccessTokenTtl(accessTtl);
        return new JwtService(properties);
    }

    @Test
    void issuesAndParsesClaims() {
        JwtService jwt = service(Duration.ofMinutes(15));
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID supportSession = UUID.randomUUID();

        String token = jwt.issue(userId, tenantId, List.of("OWNER"), false, supportSession);
        AuthPrincipal principal = jwt.parse(token);

        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.tenantId()).isEqualTo(tenantId);
        assertThat(principal.roles()).containsExactly("OWNER");
        assertThat(principal.platformAdmin()).isFalse();
        assertThat(principal.supportSessionId()).isEqualTo(supportSession);
        assertThat(jwt.accessTokenTtlSeconds()).isEqualTo(900);
    }

    @Test
    void parsesPlatformAdminWithoutTenant() {
        JwtService jwt = service(Duration.ofMinutes(15));
        String token = jwt.issue(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"), true, null);
        AuthPrincipal principal = jwt.parse(token);

        assertThat(principal.tenantId()).isNull();
        assertThat(principal.platformAdmin()).isTrue();
        assertThat(principal.supportSessionId()).isNull();
    }

    @Test
    void rejectsExpiredTokens() {
        JwtService jwt = service(Duration.ofSeconds(-5));
        String token = jwt.issue(UUID.randomUUID(), null, List.of(), false, null);
        assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTamperedTokens() {
        JwtService jwt = service(Duration.ofMinutes(15));
        String token = jwt.issue(UUID.randomUUID(), null, List.of(), false, null);
        String tampered = token.substring(0, token.length() - 2) + "aa";
        assertThatThrownBy(() -> jwt.parse(tampered)).isInstanceOf(JwtException.class);
    }
}
