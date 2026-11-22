package com.rmbreeding.api.billing;

import com.rmbreeding.api.billing.BillingDtos.*;
import com.rmbreeding.api.identity.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AuditService auditService;

    private ObjectMapper objectMapper;
    private BillingService billingService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        billingService = new BillingService(jdbcTemplate, auditService, objectMapper);
    }

    @Test
    void listPlansReturnsAvailablePlans() {
        UUID planId = UUID.randomUUID();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(
                new PlanDto(planId, "PRO", "Professionnel", 2490000, "XOF", "MONTH", 0, java.util.Map.of(), true)
        ));

        List<PlanDto> plans = billingService.listPlans();
        assertThat(plans).hasSize(1);
        assertThat(plans.getFirst().code()).isEqualTo("PRO");
    }

    @Test
    void createCheckoutSessionGeneratesValidSessionUrl() {
        UUID tenantId = UUID.randomUUID();
        CheckoutRequest request = new CheckoutRequest("PRO", "https://app.rmbreeding.com/success", "https://app.rmbreeding.com/cancel");

        CheckoutResponse response = billingService.createCheckoutSession(tenantId, request);
        assertThat(response.checkoutUrl()).startsWith("https://checkout.stripe.com/pay/cs_test_");
        assertThat(response.sessionId()).startsWith("cs_test_");
    }

    @Test
    void createPortalSessionGeneratesValidPortalUrl() {
        UUID tenantId = UUID.randomUUID();
        CustomerPortalResponse response = billingService.createPortalSession(tenantId);
        assertThat(response.portalUrl()).startsWith("https://billing.stripe.com/p/session/test_");
    }

    @Test
    void changePlanUpdatesSubscriptionAndQuotas() {
        UUID tenantId = UUID.randomUUID();
        when(jdbcTemplate.update(anyString(), eq(tenantId.toString()), eq(tenantId), eq("ENTERPRISE"))).thenReturn(1);
        when(jdbcTemplate.update(anyString(), eq(tenantId.toString()), eq(tenantId), eq("ENTERPRISE"))).thenReturn(1);

        billingService.changePlan(tenantId, "ENTERPRISE");

        verify(auditService).record(eq("CHANGE_PLAN"), eq("SUBSCRIPTION"), eq(tenantId), isNull(), any());
    }

    @Test
    void cancelAndReactivateSubscriptionWorkNominally() {
        UUID tenantId = UUID.randomUUID();

        billingService.cancelSubscription(tenantId);
        verify(auditService).record(eq("CANCEL_SUBSCRIPTION"), eq("SUBSCRIPTION"), eq(tenantId));

        billingService.reactivateSubscription(tenantId);
        verify(auditService).record(eq("REACTIVATE_SUBSCRIPTION"), eq("SUBSCRIPTION"), eq(tenantId));
    }

    @Test
    void initiateMobileMoneyCreatesPendingTransaction() {
        UUID tenantId = UUID.randomUUID();
        MobileMoneyPaymentRequest request = new MobileMoneyPaymentRequest("STARTER", "+2250700000000", "ORANGE", 990000);

        MobileMoneyPaymentResponse response = billingService.initiateMobileMoney(tenantId, request);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.providerReference()).startsWith("MOM-");
        assertThat(response.instructions()).contains("+2250700000000");
    }

    @Test
    void processWebhookEnforcesIdempotency() {
        when(jdbcTemplate.update(anyString(), eq("STRIPE"), eq("evt_123"), eq("invoice.paid"), anyString())).thenReturn(1);
        boolean firstRun = billingService.processWebhook("STRIPE", "evt_123", "invoice.paid", "{}");
        assertThat(firstRun).isTrue();

        when(jdbcTemplate.update(anyString(), eq("STRIPE"), eq("evt_123"), eq("invoice.paid"), anyString())).thenReturn(0);
        boolean duplicateRun = billingService.processWebhook("STRIPE", "evt_123", "invoice.paid", "{}");
        assertThat(duplicateRun).isFalse();
    }
}
