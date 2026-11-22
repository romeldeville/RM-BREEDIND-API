package com.rmbreeding.api.billing;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class BillingDtos {

    private BillingDtos() {}

    public record PlanDto(
            UUID id,
            String code,
            String name,
            int priceCents,
            String currency,
            String interval,
            int trialDays,
            Map<String, Object> quotas,
            boolean active
    ) {}

    public record SubscriptionDto(
            UUID id,
            UUID tenantId,
            String planCode,
            String planName,
            String provider,
            String status,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant trialEndsAt,
            boolean isTrialActive
    ) {}

    public record CheckoutRequest(
            String planCode,
            String successUrl,
            String cancelUrl
    ) {}

    public record CheckoutResponse(
            String checkoutUrl,
            String sessionId
    ) {}

    public record CustomerPortalResponse(
            String portalUrl
    ) {}

    public record ChangePlanRequest(
            String planCode
    ) {}

    public record InvoiceDto(
            UUID id,
            String number,
            int amountCents,
            String currency,
            String status,
            Instant issuedAt,
            Instant paidAt,
            String pdfKey
    ) {}

    public record MobileMoneyPaymentRequest(
            String planCode,
            String phoneNumber,
            String provider, // e.g. "ORANGE", "MTN", "MOOV", "WAVE"
            int amountCents
    ) {}

    public record MobileMoneyPaymentResponse(
            String transactionId,
            String status,
            String providerReference,
            String instructions
    ) {}
}
