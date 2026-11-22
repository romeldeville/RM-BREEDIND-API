package com.rmbreeding.api.config;

import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        IdempotencyProperties properties = new IdempotencyProperties();
        properties.setTtl(Duration.ofHours(24));
        service = new IdempotencyService(jdbcTemplate, properties);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void hashIsStableAndDependsOnBody() {
        assertThat(IdempotencyService.hash("payload")).isEqualTo(IdempotencyService.hash("payload"));
        assertThat(IdempotencyService.hash("payload")).isNotEqualTo(IdempotencyService.hash("other"));
        assertThat(IdempotencyService.hash("payload")).hasSize(64);
    }

    @Test
    void findReturnsEmptyWhenNoStoredResponse() {
        when(jdbcTemplate.query(anyString(), anyExtractor(), any(), any())).thenReturn(Optional.empty());

        Optional<IdempotencyService.StoredResponse> response = service.find("key", "hash");

        assertThat(response).isEmpty();
    }

    @Test
    void findMapsStoredResponse() throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ResultSetExtractor<Optional<IdempotencyService.StoredResponse>>> extractor =
                ArgumentCaptor.forClass(ResultSetExtractor.class);
        when(jdbcTemplate.query(anyString(), extractor.capture(), any(), any())).thenReturn(Optional.empty());

        service.find("key", "hash");

        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getString("request_hash")).thenReturn("hash");
        when(rs.getInt("status")).thenReturn(201);
        when(rs.getString("response_body")).thenReturn("{\"id\":1}");
        when(rs.getString("response_content_type")).thenReturn("application/json");

        Optional<IdempotencyService.StoredResponse> stored = extractor.getValue().extractData(rs);

        assertThat(stored).isPresent();
        assertThat(stored.get().status()).isEqualTo(201);
        assertThat(stored.get().body()).isEqualTo("{\"id\":1}");
        assertThat(stored.get().requestHash()).isEqualTo("hash");
    }

    @Test
    void startAndCompleteUseTenantFromContext() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        service.start("key", "hash");
        service.complete("key", 200, "{}", "application/json");

        // `start` passe le tenant en 2e argument, `complete` en 5e : on distingue les deux
        // instructions (même surcharge JdbcTemplate.update à 6 paramètres).
        verify(jdbcTemplate).update(contains("insert into idempotency_keys"),
                any(), eq(tenantId), any(), any(), any());
        verify(jdbcTemplate).update(contains("update idempotency_keys"),
                any(), any(), any(), eq(tenantId), any());
    }

    @Test
    void startFallsBackToAnonymousTenantWithoutContext() {
        service.start("key", "hash");

        verify(jdbcTemplate).update(anyString(), any(), eq(new UUID(0L, 0L)), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private static ResultSetExtractor<Optional<IdempotencyService.StoredResponse>> anyExtractor() {
        return any(ResultSetExtractor.class);
    }
}
