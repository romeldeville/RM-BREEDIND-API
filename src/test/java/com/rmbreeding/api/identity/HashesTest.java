package com.rmbreeding.api.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashesTest {

    @Test
    void sha256IsStableAndHexEncoded() {
        assertThat(Hashes.sha256("rm-breeding"))
                .hasSize(64)
                .isEqualTo(Hashes.sha256("rm-breeding"));
        assertThat(Hashes.sha256("rm-breeding")).isNotEqualTo(Hashes.sha256("rm-breeding "));
    }

    @Test
    void matchesUsesConstantTimeComparison() {
        String hash = Hashes.sha256("token-value");
        assertThat(Hashes.matches("token-value", hash)).isTrue();
        assertThat(Hashes.matches("other-value", hash)).isFalse();
    }

    @Test
    void randomTokenIsUrlSafeAndUnique() {
        String first = Hashes.randomToken();
        String second = Hashes.randomToken();
        assertThat(first).hasSizeGreaterThan(30).isNotEqualTo(second);
        assertThat(first).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void numericCodeKeepsRequestedLength() {
        for (int i = 0; i < 50; i++) {
            String code = Hashes.numericCode(6);
            assertThat(code).matches("\\d{6}");
        }
    }
}
