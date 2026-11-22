package com.rmbreeding.api.shared;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorTest {

    @Test
    void encodesAndDecodesRoundTrip() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-11-11T10:15:30Z");
        Cursor cursor = new Cursor(createdAt, id);

        Cursor decoded = Cursor.decode(cursor.encode());

        assertThat(decoded.createdAt()).isEqualTo(createdAt);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void returnsNullForBlankInput() {
        assertThat(Cursor.decode(null)).isNull();
        assertThat(Cursor.decode(" ")).isNull();
    }

    @Test
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> Cursor.decode("not-a-cursor"))
                .isInstanceOf(BusinessRuleException.class);
    }
}
