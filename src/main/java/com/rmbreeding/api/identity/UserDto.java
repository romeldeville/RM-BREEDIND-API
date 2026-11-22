package com.rmbreeding.api.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String firstName,
        String lastName,
        List<String> roles,
        Instant createdAt) {
}
