package com.rmbreeding.api;

import com.rmbreeding.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliedAllMigrations() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);

        assertThat(applied).isEqualTo(20);
    }

    @Test
    void rowLevelSecurityIsEnabled() {
        Integer rlsTables = jdbcTemplate.queryForObject("""
                select count(*) from pg_tables
                where schemaname = 'public' and rowsecurity = true
                """, Integer.class);

        assertThat(rlsTables).isGreaterThanOrEqualTo(60);
    }

    @Test
    void demoSeedIsLoaded() {
        Integer species = jdbcTemplate.queryForObject("select count(*) from species", Integer.class);
        Integer plans = jdbcTemplate.queryForObject("select count(*) from plans", Integer.class);

        assertThat(species).isGreaterThanOrEqualTo(8);
        assertThat(plans).isEqualTo(4);
    }
}
