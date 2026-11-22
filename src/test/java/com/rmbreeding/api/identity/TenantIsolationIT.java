package com.rmbreeding.api.identity;

import com.rmbreeding.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P16-10/17 (P17-20) - isolation tenant via RLS : un contexte tenant ne voit pas les données
 * d'un autre tenant, et l'absence de contexte ne renvoie aucune ligne.
 *
 * <p>Le test s'exécute sur une connexion unique afin que {@code set_config}/{@code RESET}
 * soient déterministes quel que soit le pool de connexions. Les policies sont évaluées sous
 * un rôle non-superuser : un superuser PostgreSQL contourne RLS même avec FORCE.</p>
 */
class TenantIsolationIT extends IntegrationTestBase {

    private static final String PROBE_ROLE = "rls_probe";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rowLevelSecurityIsolatesTenantScopedTables() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID farmA = UUID.randomUUID();
        UUID farmB = UUID.randomUUID();

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            createProbeRole(connection);
            try {
                execute(connection, "SET ROLE " + PROBE_ROLE);

                insertTenant(connection, tenantA, "Tenant A");
                insertTenant(connection, tenantB, "Tenant B");

                setTenant(connection, tenantA);
                insertFarm(connection, farmA, tenantA, "Ferme A", "A1");
                assertThat(count(connection,
                        "SELECT count(*) FROM farms WHERE tenant_id = ?", tenantA)).isEqualTo(1);
                assertThat(count(connection,
                        "SELECT count(*) FROM farms WHERE tenant_id = ?", tenantB)).isZero();
                assertThat(count(connection,
                        "SELECT count(*) FROM farms WHERE tenant_id <> ?", tenantA)).isZero();

                setTenant(connection, tenantB);
                insertFarm(connection, farmB, tenantB, "Ferme B", "B1");
                assertThat(count(connection,
                        "SELECT count(*) FROM farms WHERE tenant_id = ?", tenantB)).isEqualTo(1);
                assertThat(count(connection,
                        "SELECT count(*) FROM farms WHERE tenant_id = ?", tenantA)).isZero();

                setTenant(connection, UUID.randomUUID());
                assertThat(count(connection, "SELECT count(*) FROM farms")).isZero();
            } finally {
                execute(connection, "RESET ROLE");
                execute(connection, "DROP OWNED BY " + PROBE_ROLE);
                execute(connection, "DROP ROLE IF EXISTS " + PROBE_ROLE);
            }
            return null;
        });
    }

    private static void createProbeRole(Connection connection) throws SQLException {
        execute(connection, "DROP ROLE IF EXISTS " + PROBE_ROLE);
        execute(connection, "CREATE ROLE " + PROBE_ROLE + " NOLOGIN");
        execute(connection, "GRANT USAGE ON SCHEMA public TO " + PROBE_ROLE);
        execute(connection,
                "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO " + PROBE_ROLE);
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void insertTenant(Connection connection, UUID id, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO tenants (id, name, slug, status, plan_code, settings)
                VALUES (?, ?, ?, 'ACTIVE', 'FREE', '{}'::jsonb)
                """)) {
            statement.setObject(1, id);
            statement.setString(2, name);
            statement.setString(3, "tenant-" + id.toString().substring(0, 8));
            statement.executeUpdate();
        }
    }

    private static void insertFarm(Connection connection, UUID id, UUID tenantId,
                                   String name, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO farms (id, tenant_id, name, code, country, currency, timezone)
                VALUES (?, ?, ?, ?, 'CI', 'XOF', 'UTC')
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, tenantId);
            statement.setString(3, name);
            statement.setString(4, code);
            statement.executeUpdate();
        }
    }

    private static void setTenant(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, false)")) {
            statement.setString(1, tenantId.toString());
            statement.execute();
        }
    }

    private static int count(Connection connection, String sql, Object argument) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, argument);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
