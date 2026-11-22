package com.rmbreeding.api;

import com.rmbreeding.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationSmokeTest extends IntegrationTestBase {

    @Test
    void healthEndpointIsUp() {
        api().get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void openApiDocumentIsAvailable() {
        api().get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("RM Breeding API"));
    }
}
