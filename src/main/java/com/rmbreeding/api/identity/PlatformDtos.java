package com.rmbreeding.api.identity;

import java.time.Instant;
import java.util.UUID;

public final class PlatformDtos {

    private PlatformDtos() {}

    public record PlatformTenantDto(
            UUID id,
            String name,
            String slug,
            String status,
            String planCode,
            int farmsCount,
            int animalsCount,
            Instant createdAt
    ) {}

    public record PlatformMetricsDto(
            long totalTenants,
            long activeTenants,
            long totalFarms,
            long totalAnimals,
            long totalUsers,
            long monthlyRecurringRevenueCents
    ) {}

    public record AnnouncementDto(
            UUID id,
            String title,
            String message,
            String severity,
            Instant startsAt,
            Instant endsAt,
            boolean active
    ) {}

    public record CreateAnnouncementRequest(
            String title,
            String message,
            String severity,
            Instant startsAt,
            Instant endsAt
    ) {}
}
