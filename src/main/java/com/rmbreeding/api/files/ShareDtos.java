package com.rmbreeding.api.files;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ShareDtos {

    private ShareDtos() {
    }

    public record ShareCreateRequest(
            UUID attachmentId,
            UUID exportJobId,
            @Size(max = 60) String entityType,
            UUID entityId,
            @Min(1) @Max(720) Integer ttlHours) {
    }

    public record ShareLinkResponse(
            UUID id,
            String url,
            String filename,
            String mimeType,
            Long sizeBytes,
            String entityType,
            UUID entityId,
            Instant expiresAt,
            Instant revokedAt,
            int accessCount,
            Instant lastAccessedAt,
            Instant createdAt) {
    }
}
