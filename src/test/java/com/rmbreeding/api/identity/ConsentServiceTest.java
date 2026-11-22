package com.rmbreeding.api.identity;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void recordsRegistrationConsentForTermsAndPrivacy() {
        PrivacyProperties properties = new PrivacyProperties();
        properties.setTermsVersion("v-terms");
        properties.setPrivacyVersion("v-privacy");
        ConsentService service = new ConsentService(jdbcTemplate, properties);
        UUID userId = UUID.randomUUID();

        service.recordRegistrationConsent(userId);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(2)).update(contains("INSERT INTO consents"), args.capture());
        List<Object[]> calls = args.getAllValues();
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0)[1]).isEqualTo(userId);
        assertThat(calls.get(0)[2]).isEqualTo(ConsentService.TERMS);
        assertThat(calls.get(0)[3]).isEqualTo("v-terms");
        assertThat(calls.get(1)[1]).isEqualTo(userId);
        assertThat(calls.get(1)[2]).isEqualTo(ConsentService.PRIVACY);
        assertThat(calls.get(1)[3]).isEqualTo("v-privacy");
    }

    @Test
    void listsConsentsForUser() {
        ConsentService service = new ConsentService(jdbcTemplate, new PrivacyProperties());
        UUID userId = UUID.randomUUID();
        when(jdbcTemplate.query(contains("FROM consents"), any(RowMapper.class), eq(userId)))
                .thenReturn(List.of(new IdentityDtos.ConsentResponse("TERMS_OF_SERVICE", "v1",
                        Instant.parse("2026-11-01T00:00:00Z"), null)));

        List<IdentityDtos.ConsentResponse> result = service.list(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo("TERMS_OF_SERVICE");
    }
}
