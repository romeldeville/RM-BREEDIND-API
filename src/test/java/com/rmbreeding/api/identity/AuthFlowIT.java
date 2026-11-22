package com.rmbreeding.api.identity;

import com.rmbreeding.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFlowIT extends IntegrationTestBase {

    @Test
    void registerLoginMeAndLogout() {
        String email = "flow-" + UUID.randomUUID() + "@rmbreeding.local";
        Map<String, Object> register = Map.of("email", email, "password", "TestPassword123!",
                "fullName", "Flow User", "consent", true);

        Map<?, ?> created = api().post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON).body(register).exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(created).isNotNull();
        String tenantId = (String) created.get("tenantId");
        assertThat(tenantId).isNotBlank();

        Map<?, ?> login = api().post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "TestPassword123!")).exchange()
                .expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(login).isNotNull();
        String accessToken = (String) login.get("accessToken");
        String refreshToken = (String) login.get("refreshToken");
        assertThat(accessToken).isNotBlank();

        api().get().uri("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken)
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.email").isEqualTo(email);

        api().get().uri("/api/v1/tenants/" + tenantId + "/quotas")
                .header("Authorization", "Bearer " + accessToken).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(4)
                .jsonPath("$[1].quotaKey").isEqualTo("farms")
                .jsonPath("$[1].used").isEqualTo(1);

        api().post().uri("/api/v1/farms")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Second", "code", "F2", "country", "CI", "currency", "XOF"))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("QUOTA_EXCEEDED");

        Map<?, ?> refreshed = api().post().uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", refreshToken)).exchange()
                .expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(refreshed).isNotNull();
        String rotated = (String) refreshed.get("accessToken");
        assertThat(rotated).isNotBlank();

        api().post().uri("/api/v1/auth/logout").header("Authorization", "Bearer " + rotated)
                .exchange().expectStatus().isNoContent();
    }

    @Test
    void locksAccountAfterFiveFailedAttempts() {
        String email = "lock-" + UUID.randomUUID() + "@rmbreeding.local";
        api().post().uri("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "TestPassword123!",
                        "fullName", "Lock User", "consent", true))
                .exchange().expectStatus().isCreated();

        for (int attempt = 0; attempt < 5; attempt++) {
            api().post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "password", "WrongPassword!"))
                    .exchange().expectStatus().isUnauthorized();
        }

        api().post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "TestPassword123!"))
                .exchange().expectStatus().isEqualTo(423)
                .expectBody().jsonPath("$.code").isEqualTo("LOCKED");
    }
}
