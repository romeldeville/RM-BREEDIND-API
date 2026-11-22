package com.rmbreeding.api.files;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AttachmentDtos {

    private AttachmentDtos() {
    }

    public record PresignRequest(
            @NotBlank @Size(max = 60) String entityType,
            @NotNull UUID entityId,
            @NotBlank @Size(max = 255) String filename,
            @Size(max = 120) String mimeType,
            @Min(1) Long sizeBytes) {
    }

    public record PresignResponse(
            UUID attachmentId,
            String uploadUrl,
            String storageKey,
            Instant expiresAt) {
    }

    public record ConfirmRequest(
            @Size(max = 64) String checksum) {
    }

    public record AttachmentResponse(
            UUID id,
            String entityType,
            UUID entityId,
            String filename,
            String mimeType,
            Long sizeBytes,
            String checksum,
            boolean confirmed,
            Instant createdAt,
            Instant confirmedAt) {
    }

    public record DownloadUrlResponse(
            String url,
            Instant expiresAt) {
    }
}
