package com.rmbreeding.api.billing;

import com.rmbreeding.api.billing.BillingDtos.*;
import com.rmbreeding.api.identity.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/plans")
    public ResponseEntity<List<PlanDto>> listPlans() {
        return ResponseEntity.ok(billingService.listPlans());
    }

    @GetMapping("/subscription")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'PLATFORM_ADMIN')")
    public ResponseEntity<SubscriptionDto> getSubscription(@AuthenticationPrincipal AuthPrincipal principal) {
        return billingService.getSubscription(principal.tenantId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasAnyRole('OWNER', 'PLATFORM_ADMIN')")
    public ResponseEntity<CheckoutResponse> createCheckoutSession(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody @Valid CheckoutRequest request) {
        return ResponseEntity.ok(billingService.createCheckoutSession(principal.tenantId(), request));
    }

    @PostMapping("/portal")
    @PreAuthorize("hasAnyRole('OWNER', 'PLATFORM_ADMIN')")
    public ResponseEntity<CustomerPortalResponse> createPortalSession(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(billingService.createPortalSession(principal.tenantId()));
    }

    @PostMapping("/change-plan")
    @PreAuthorize("hasAnyRole('OWNER', 'PLATFORM_ADMIN')")
    public ResponseEntity<Void> changePlan(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody @Valid ChangePlanRequest request) {
        billingService.changePlan(principal.tenantId(), request.planCode());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasAnyRole('OWNER', 'PLATFORM_ADMIN')")
    public ResponseEntity<Void> cancelSubscription(@AuthenticationPrincipal AuthPrincipal principal) {
        billingService.cancelSubscription(principal.tenantId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reactivate")
    @PreAuthorize("hasAnyRole('OWNER', 'PLATFORM_ADMIN')")
    public ResponseEntity<Void> reactivateSubscription(@AuthenticationPrincipal AuthPrincipal principal) {
        billingService.reactivateSubscription(principal.tenantId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('OWNER', 'ACCOUNTANT', 'PLATFORM_ADMIN')")
    public ResponseEntity<List<InvoiceDto>> listInvoices(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(billingService.listInvoices(principal.tenantId()));
    }

    @PostMapping("/mobile-money")
    @PreAuthorize("hasAnyRole('OWNER', 'PLATFORM_ADMIN')")
    public ResponseEntity<MobileMoneyPaymentResponse> initiateMobileMoney(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody @Valid MobileMoneyPaymentRequest request) {
        return ResponseEntity.ok(billingService.initiateMobileMoney(principal.tenantId(), request));
    }
}
