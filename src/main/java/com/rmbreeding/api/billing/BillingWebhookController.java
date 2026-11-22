package com.rmbreeding.api.billing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing/webhooks")
public class BillingWebhookController {

    private final BillingService billingService;

    public BillingWebhookController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> stripeWebhook(
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            @RequestBody String payload) {
        String eventId = "evt_stripe_" + UUID.nameUUIDFromBytes(payload.getBytes());
        billingService.processWebhook("STRIPE", eventId, "stripe.event", payload);
        return ResponseEntity.ok("Received");
    }

    @PostMapping("/mobile-money")
    public ResponseEntity<String> mobileMoneyWebhook(
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String payload) {
        String eventId = "evt_mom_" + UUID.nameUUIDFromBytes(payload.getBytes());
        billingService.processWebhook("MOBILE_MONEY", eventId, "mobile_money.callback", payload);
        return ResponseEntity.ok("Received");
    }
}
