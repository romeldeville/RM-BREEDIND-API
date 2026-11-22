package com.rmbreeding.api.identity;

import com.rmbreeding.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P16-16/19 - contrôle d'accès RBAC de bout en bout sur les endpoints sensibles
 * (journal d'audit et export de tenant) : anonyme 401, OWNER autorisé, MEMBER interdit.
 */
class RbacAuditIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void auditAndTenantExportEnforceRoles() {
        String ownerEmail = "rbac-owner-" + UUID.randomUUID() + "@rmbreeding.local";
        RegisterResult owner = register(ownerEmail);
        String ownerToken = login(ownerEmail);

        // Anonyme : authentification requise.
        api().get().uri("/api/v1/audit")
                .exchange().expectStatus().isUnauthorized();

        // OWNER : consultation du journal et export autorisés.
        api().get().uri("/api/v1/audit")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange().expectStatus().isOk();
        api().get().uri("/api/v1/tenants/" + owner.tenantId() + "/export")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tenant.id").isEqualTo(owner.tenantId().toString());

        // MEMBER du même tenant : endpoints OWNER interdits.
        String memberEmail = "rbac-member-" + UUID.randomUUID() + "@rmbreeding.local";
        RegisterResult member = register(memberEmail);
        moveMembership(member.userId(), owner.tenantId(), "MEMBER");
        String memberToken = login(memberEmail);

        api().get().uri("/api/v1/audit")
                .header("Authorization", "Bearer " + memberToken)
                .exchange().expectStatus().isForbidden();
        api().get().uri("/api/v1/tenants/" + owner.tenantId() + "/export")
                .header("Authorization", "Bearer " + memberToken)
                .exchange().expectStatus().isForbidden();
    }

    private RegisterResult register(String email) {
        Map<?, ?> body = api().post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "TestPassword123!",
                        "fullName", "RBAC User", "consent", true))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(body).isNotNull();
        return new RegisterResult(
                UUID.fromString((String) body.get("userId")),
                UUID.fromString((String) body.get("tenantId")));
    }

    private String login(String email) {
        Map<?, ?> body = api().post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "TestPassword123!"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(body).isNotNull();
        String accessToken = (String) body.get("accessToken");
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }

    private void moveMembership(UUID userId, UUID tenantId, String role) {
        int updated = jdbcTemplate.update("""
                UPDATE memberships SET tenant_id = ?, role = ?, status = 'ACTIVE'
                WHERE user_id = ?
                """, tenantId, role, userId);
        assertThat(updated).isEqualTo(1);
    }

    private record RegisterResult(UUID userId, UUID tenantId) {
    }
}
