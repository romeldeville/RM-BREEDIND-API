package com.rmbreeding.api.identity;

import tools.jackson.databind.ObjectMapper;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        AuthContext.clear();
    }

    @Test
    void recordWritesAppendOnlyEntryWithContext() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID supportSessionId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        AuthContext.set(new AuthPrincipal(actorId, tenantId, List.of("OWNER"), false, supportSessionId));

        AuditService service = new AuditService(jdbcTemplate, new ObjectMapper());
        service.recordChange("FARM_UPDATED", "farm", entityId, Map.of("name", "A"), Map.of("name", "B"));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("INSERT INTO audit_log"), args.capture());
        Object[] values = args.getValue();
        assertThat(values[1]).isEqualTo(tenantId);
        assertThat(values[2]).isEqualTo(actorId);
        assertThat(values[3]).isEqualTo(supportSessionId);
        assertThat(values[4]).isEqualTo("FARM_UPDATED");
        assertThat(values[5]).isEqualTo("farm");
        assertThat(values[6]).isEqualTo(entityId);
        assertThat(String.valueOf(values[7])).contains("\"name\"");
        assertThat(String.valueOf(values[8])).contains("\"name\"");
    }

    @Test
    void listQueriesByTenantWithFilters() {
        UUID tenantId = UUID.randomUUID();
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-02T00:00:00Z");
        UUID entityId = UUID.randomUUID();
        AuditService service = new AuditService(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        List<AuditDtos.AuditEntryResponse> result = service.list(tenantId, from, to, "farm", entityId, 500);

        assertThat(result).isEmpty();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), args.capture());
        assertThat(sql.getValue()).contains("tenant_id = ?").contains("entity_type = ?").contains("entity_id = ?");
        Object[] values = args.getValue();
        assertThat(values[0]).isEqualTo(tenantId);
        assertThat(values[1]).isEqualTo(Timestamp.from(from));
        assertThat(values[2]).isEqualTo(Timestamp.from(to));
        assertThat(values[3]).isEqualTo("farm");
        assertThat(values[4]).isEqualTo(entityId);
        assertThat(values[5]).isEqualTo(200); // capped
    }
}
