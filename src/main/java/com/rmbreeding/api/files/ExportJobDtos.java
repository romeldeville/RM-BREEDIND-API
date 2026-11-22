package com.rmbreeding.api.files;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class ExportJobDtos {

    private ExportJobDtos() {
    }

    public record ExportJobCreateRequest(
            @NotBlank @Size(max = 40) String type,
            Map<String, Object> params) {
    }

    public record ExportJobResponse(
            UUID id,
            String type,
            String status,
            Map<String, Object> params,
            String filename,
            String mimeType,
            Long sizeBytes,
            Integer rowCount,
            String errorMessage,
            String downloadUrl,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt) {
    }

    public record ExportDownloadResponse(
            String url,
            Instant expiresAt) {
    }
}
