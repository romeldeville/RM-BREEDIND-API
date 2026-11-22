package com.rmbreeding.api.identity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void computesRfc6238Vector() {
        assertThat(Totp.code(RFC_SECRET, Instant.ofEpochSecond(59))).isEqualTo("287082");
    }

    @Test
    void acceptsCodesWithinOneStepWindow() {
        Instant at = Instant.ofEpochSecond(1_700_000_000);
        String code = Totp.code(RFC_SECRET, at);
        assertThat(Totp.verify(RFC_SECRET, code, at)).isTrue();
        assertThat(Totp.verify(RFC_SECRET, code, at.plusSeconds(30))).isTrue();
        assertThat(Totp.verify(RFC_SECRET, code, at.plusSeconds(120))).isFalse();
    }

    @Test
    void rejectsMalformedCodes() {
        assertThat(Totp.verify(RFC_SECRET, null, Instant.now())).isFalse();
        assertThat(Totp.verify(RFC_SECRET, "12345", Instant.now())).isFalse();
        assertThat(Totp.verify(RFC_SECRET, "abcdef", Instant.now())).isFalse();
    }

    @Test
    void generatedSecretsRoundTrip() {
        String secret = Totp.generateSecret();
        assertThat(secret).hasSize(32);
        Instant at = Instant.now();
        assertThat(Totp.verify(secret, Totp.code(secret, at), at)).isTrue();
    }
}
