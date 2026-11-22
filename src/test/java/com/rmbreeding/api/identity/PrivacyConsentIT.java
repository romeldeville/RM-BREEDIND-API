package com.rmbreeding.api.identity;

import com.rmbreeding.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P16-03/05 intégration : export des données personnelles et enregistrement des consentements.
 */
class PrivacyConsentIT extends IntegrationTestBase {

    @Test
    void registerRecordsConsentsAndExportsPersonalData() {
        String email = "privacy-" + UUID.randomUUID() + "@rmbreeding.local";
        Map<String, Object> register = Map.of(
                "email", email,
                "password", "TestPassword123!",
                "fullName", "Privacy User",
                "consent", true);

        api().post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON).body(register).exchange()
                .expectStatus().isCreated();

        Map<?, ?> login = api().post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "TestPassword123!")).exchange()
                .expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(login).isNotNull();
        String accessToken = (String) login.get("accessToken");
        assertThat(accessToken).isNotBlank();

        api().get().uri("/api/v1/privacy/consents")
                .header("Authorization", "Bearer " + accessToken)
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2);

        api().get().uri("/api/v1/privacy/export")
                .header("Authorization", "Bearer " + accessToken)
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.profile.email").isEqualTo(email)
                .jsonPath("$.memberships.length()").isEqualTo(1)
                .jsonPath("$.consents.length()").isEqualTo(2)
                .jsonPath("$.formatVersion").isEqualTo("1.0");
    }
}
