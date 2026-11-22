package com.rmbreeding.api.notifications;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record TokenRequest(
            @NotBlank @Size(max = 4096) String token,
            @NotBlank @Pattern(regexp = "ANDROID|IOS|WEB") String platform) {
    }

    public record TokenResponse(
            UUID id,
            String platform,
            Instant lastSeenAt) {
    }

    public record PreferenceResponse(
            String type,
            String channel,
            boolean enabled) {
    }

    public record PreferenceUpdate(
            @NotBlank @Size(max = 60) String type,
            @NotBlank @Size(max = 20) String channel,
            boolean enabled) {
    }

    public record PreferencesUpdateRequest(
            @NotEmpty List<@Valid PreferenceUpdate> items) {
    }

    public record LogResponse(
            UUID id,
            String channel,
            String type,
            String status,
            Instant sentAt,
            String error) {
    }
}
