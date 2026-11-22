package com.rmbreeding.api.contract;

import com.rmbreeding.api.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P17-01 - tests critiques T-01 à T-10 au niveau HTTP (complètent les tests unitaires).
 *
 * <ul>
 *   <li>T-01 : saillie sur femelle gestante refusée (409) ;</li>
 *   <li>T-03 : vente pendant un délai d'attente refusée (409) ;</li>
 *   <li>T-08 : le soigneur (WORKER) n'accède pas aux routes ventes/finances (403) ;</li>
 *   <li>T-09 : une ferme d'un autre tenant est invisible (404).</li>
 * </ul>
 *
 * <p>T-02, T-04 à T-07 et T-10 sont couverts par les tests unitaires de service
 * (voir {@code docs/architecture/recette-tests-critiques.md}) ; T-11 dépend de P18-09.</p>
 */
class CriticalRulesIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("T-01 : une seconde saillie sur une femelle gestante renvoie 409")
    void matingOnPregnantFemaleIsRejected() {
        Session session = register("t01-" + UUID.randomUUID() + "@rmbreeding.local");
        UUID femaleId = createAnimal(session, "FEMALE");

        Map<?, ?> cycle = post(session, "/api/v1/reproduction/cycles",
                Map.of("femaleId", femaleId))
                .expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(cycle).isNotNull();

        post(session, "/api/v1/reproduction/services",
                Map.of("femaleId", femaleId, "matedAt", Instant.now().toString()))
                .expectStatus().isCreated();

        post(session, "/api/v1/reproduction/diagnoses",
                Map.of("cycleId", cycle.get("id"), "diagnosedAt", Instant.now().toString(),
                        "result", "POSITIVE"))
                .expectStatus().is2xxSuccessful();

        post(session, "/api/v1/reproduction/services",
                Map.of("femaleId", femaleId, "matedAt", Instant.now().toString()))
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("CONFLICT");
    }

    @Test
    @DisplayName("T-03 : une vente pendant un délai d'attente renvoie 409")
    void saleDuringWithdrawalIsRejected() {
        Session session = register("t03-" + UUID.randomUUID() + "@rmbreeding.local");
        UUID animalId = createAnimal(session, "FEMALE");

        Map<?, ?> product = post(session, "/api/v1/health/products",
                Map.of("name", "Antibiotique T-03", "type", "ANTIBIOTIC", "withdrawalMeatDays", 10))
                .expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(product).isNotNull();

        post(session, "/api/v1/health/treatments",
                Map.of("animalId", animalId, "productId", product.get("id"),
                        "startDate", LocalDate.now().toString()))
                .expectStatus().isCreated();

        post(session, "/api/v1/sales",
                Map.of("type", "ANIMAL", "soldAt", Instant.now().toString(),
                        "lines", List.of(Map.of("itemType", "ANIMAL", "animalId", animalId,
                                "quantity", 1, "unitPrice", 150000))))
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("CONFLICT");
    }

    @Test
    @DisplayName("T-08 : WORKER interdit sur ventes/finances, ACCOUNTANT autorisé")
    void workerCannotAccessFinanceAndSalesRoutes() {
        Session owner = register("t08-owner-" + UUID.randomUUID() + "@rmbreeding.local");

        String workerEmail = "t08-worker-" + UUID.randomUUID() + "@rmbreeding.local";
        Session worker = register(workerEmail);
        moveMembership(worker.userId(), owner.tenantId(), "WORKER");
        String workerToken = login(workerEmail);

        for (String route : List.of("/api/v1/expenses", "/api/v1/revenues",
                "/api/v1/financial-statements", "/api/v1/sales", "/api/v1/customers")) {
            api().get().uri(route).header("Authorization", "Bearer " + workerToken)
                    .exchange().expectStatus().isForbidden();
        }

        // Le refus est limité aux finances : le soigneur garde l'accès au cheptel.
        api().get().uri("/api/v1/animals").header("Authorization", "Bearer " + workerToken)
                .exchange().expectStatus().isOk();

        String accountantEmail = "t08-accountant-" + UUID.randomUUID() + "@rmbreeding.local";
        Session accountant = register(accountantEmail);
        moveMembership(accountant.userId(), owner.tenantId(), "ACCOUNTANT");
        String accountantToken = login(accountantEmail);

        for (String route : List.of("/api/v1/expenses", "/api/v1/revenues",
                "/api/v1/financial-statements", "/api/v1/sales", "/api/v1/customers")) {
            api().get().uri(route).header("Authorization", "Bearer " + accountantToken)
                    .exchange().expectStatus().isOk();
        }
    }

    @Test
    @DisplayName("T-09 : une ferme d'un autre tenant renvoie 404")
    void crossTenantFarmAccessIsDenied() {
        Session tenantA = register("t09-a-" + UUID.randomUUID() + "@rmbreeding.local");
        Session tenantB = register("t09-b-" + UUID.randomUUID() + "@rmbreeding.local");

        api().get().uri("/api/v1/farms/" + tenantB.farmId())
                .header("Authorization", "Bearer " + tenantA.token())
                .exchange().expectStatus().isNotFound();

        api().patch().uri("/api/v1/farms/" + tenantB.farmId())
                .header("Authorization", "Bearer " + tenantA.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Intrusion"))
                .exchange().expectStatus().isNotFound();

        // Contrôle positif : le propriétaire de la ferme y accède bien.
        api().get().uri("/api/v1/farms/" + tenantB.farmId())
                .header("Authorization", "Bearer " + tenantB.token())
                .exchange().expectStatus().isOk();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Session register(String email) {
        Map<?, ?> body = api().post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "TestPassword123!",
                        "fullName", "Critical User", "consent", true))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(body).isNotNull();

        String token = login(email);
        UUID farmId = farmId(token);
        return new Session(UUID.fromString((String) body.get("userId")),
                UUID.fromString((String) body.get("tenantId")), token, farmId, email);
    }

    private String login(String email) {
        Map<?, ?> body = api().post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "TestPassword123!"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(body).isNotNull();
        String token = (String) body.get("accessToken");
        assertThat(token).isNotBlank();
        return token;
    }

    private UUID farmId(String token) {
        List<?> farms = api().get().uri("/api/v1/farms")
                .header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isOk()
                .expectBody(List.class).returnResult().getResponseBody();
        assertThat(farms).isNotEmpty();
        return UUID.fromString((String) ((Map<?, ?>) farms.getFirst()).get("id"));
    }

    private UUID createAnimal(Session session, String sex) {
        String speciesCode = "bovin-" + UUID.randomUUID().toString().substring(0, 8);
        Map<?, ?> species = post(session, "/api/v1/species", Map.of(
                "name", "Bovin " + speciesCode,
                "code", speciesCode,
                "productionType", "MEAT",
                "trackingMode", "INDIVIDUAL",
                "gestationDays", 283,
                "heatCycleDays", 21,
                "weaningAgeDays", 60))
                .expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(species).isNotNull();

        Map<?, ?> animal = post(session, "/api/v1/animals", Map.of(
                "farmId", session.farmId(),
                "speciesId", species.get("id"),
                "identifier", "AN-" + UUID.randomUUID().toString().substring(0, 8),
                "sex", sex,
                "birthDate", LocalDate.now().minusYears(2).toString()))
                .expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(animal).isNotNull();
        return UUID.fromString((String) animal.get("id"));
    }

    private org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec post(
            Session session, String path, Object body) {
        return api().post().uri(path)
                .header("Authorization", "Bearer " + session.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private void moveMembership(UUID userId, UUID tenantId, String role) {
        int updated = jdbcTemplate.update("""
                UPDATE memberships SET tenant_id = ?, role = ?, status = 'ACTIVE'
                WHERE user_id = ?
                """, tenantId, role, userId);
        assertThat(updated).isEqualTo(1);
    }

    private record Session(UUID userId, UUID tenantId, String token, UUID farmId, String email) {
    }
}
