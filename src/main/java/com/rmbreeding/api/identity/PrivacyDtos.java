package com.rmbreeding.api.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PrivacyDtos {

    private PrivacyDtos() {
    }

    public record Profile(
            UUID id,
            String email,
            String fullName,
            String phone,
            String locale,
            String status,
            boolean mfaEnabled,
            Instant emailVerifiedAt,
            Instant phoneVerifiedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record Membership(UUID tenantId, String tenantName, String role, String status,
            List<UUID> farmScope, Instant createdAt) {
    }

    public record PersonalDataExport(
            Profile profile,
            List<Membership> memberships,
            List<IdentityDtos.ConsentResponse> consents,
            List<AuditDtos.AuditEntryResponse> auditTrail,
            Instant exportedAt,
            String formatVersion) {
    }
}
