package com.rmbreeding.api.files;

import com.rmbreeding.api.config.StorageProperties;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShareTokenServiceTest {

    private ShareTokenService service;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setShareSecret("unit-test-share-secret-0123456789abcdef");
        service = new ShareTokenService(properties);
    }

    @Test
    void issuesAndVerifiesToken() {
        UUID tenantId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        Instant expiresAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                .plusSeconds(3600);

        String token = service.issue(tenantId, linkId, expiresAt);
        ShareTokenService.ShareTokenPayload payload = service.verify(token);

        assertThat(payload.tenantId()).isEqualTo(tenantId);
        assertThat(payload.linkId()).isEqualTo(linkId);
        assertThat(payload.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void rejectsTamperedToken() {
        String token = service.issue(UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plusSeconds(3600));
        String tampered = token.substring(0, token.length() - 2) + "aa";

        assertThatThrownBy(() -> service.verify(tampered))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> service.verify("not-a-token"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.verify(""))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsExpiredToken() {
        String token = service.issue(UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().minusSeconds(10));

        assertThatThrownBy(() -> service.verify(token))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.EXPIRED));
    }
}
