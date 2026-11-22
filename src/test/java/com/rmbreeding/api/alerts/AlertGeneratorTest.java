package com.rmbreeding.api.alerts;

import com.rmbreeding.api.feeding.FeedingDtos;
import com.rmbreeding.api.feeding.FeedingService;
import com.rmbreeding.api.health.HealthDtos;
import com.rmbreeding.api.health.HealthService;
import com.rmbreeding.api.herd.LotService;
import com.rmbreeding.api.production.ProductionService;
import com.rmbreeding.api.reproduction.ReproductionDtos;
import com.rmbreeding.api.reproduction.ReproductionService;
import com.rmbreeding.api.referential.SiteService;
import com.rmbreeding.api.sales.SalesDtos;
import com.rmbreeding.api.sales.SalesService;
import com.rmbreeding.api.shared.PageResponse;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertGeneratorTest {

    @Mock
    private ReproductionService reproductionService;
    @Mock
    private HealthService healthService;
    @Mock
    private FeedingService feedingService;
    @Mock
    private ProductionService productionService;
    @Mock
    private LotService lotService;
    @Mock
    private SiteService siteService;
    @Mock
    private SalesService salesService;
    @Mock
    private com.rmbreeding.api.identity.FarmService farmService;
    @Mock
    private AlertsService alertsService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID farmId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void reproductionGeneratorRaisesDiagnosisAndFarrowing() {
        UUID cycleId = UUID.randomUUID();
        UUID femaleId = UUID.randomUUID();
        ReproductionDtos.CycleResponse cycle = new ReproductionDtos.CycleResponse(cycleId, farmId, femaleId,
                "MATED", 1, today.minusDays(40), null, today, today.minusDays(1), Instant.now(), Instant.now());
        when(reproductionService.listCycles(null, null, 100, null))
                .thenReturn(PageResponse.last(List.of(cycle)));
        when(reproductionService.getCycle(cycleId)).thenReturn(new ReproductionDtos.CycleDetailResponse(
                cycle, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));

        new ReproductionAlertGenerator(reproductionService, alertsService).generate(today);

        verify(alertsService).raise(eq(tenantId), eq(farmId), eq("PREGNANCY_DIAGNOSIS_DUE"), eq("IMPORTANT"),
                any(), any(), eq(today.minusDays(1)), eq("cycle"), eq(cycleId));
        verify(alertsService).raise(eq(tenantId), eq(farmId), eq("FARROWING_DUE"), eq("CRITICAL"),
                any(), any(), eq(today.minusDays(7)), eq("cycle"), eq(cycleId));
    }

    @Test
    void healthGeneratorRaisesWithdrawalStart() {
        when(alertsService.requireDefaultFarmId()).thenReturn(farmId);
        UUID withdrawalId = UUID.randomUUID();
        when(healthService.listWithdrawals("ACTIVE")).thenReturn(List.of(new HealthDtos.WithdrawalResponse(
                withdrawalId, UUID.randomUUID(), UUID.randomUUID(), null, "ANTIBIOTIC",
                today.minusDays(1), today.plusDays(5), "ACTIVE")));
        when(healthService.listQuarantines("ACTIVE")).thenReturn(List.of());

        new HealthAlertGenerator(healthService, alertsService).generate(today);

        verify(alertsService).raise(eq(tenantId), eq(farmId), eq("WITHDRAWAL_START"), eq("CRITICAL"),
                any(), any(), eq(today.minusDays(1)), eq("withdrawal"), eq(withdrawalId));
    }

    @Test
    void feedGeneratorRaisesLowStock() {
        UUID feedId = UUID.randomUUID();
        when(alertsService.requireDefaultFarmId()).thenReturn(farmId);
        when(feedingService.lowStock(null)).thenReturn(List.of(new FeedingDtos.LowStockAlertResponse(
                feedId, "Maïs", "KG", BigDecimal.ONE, BigDecimal.TEN)));
        when(feedingService.expiringReceipts(any(LocalDate.class))).thenReturn(List.of());
        when(lotService.list("OPEN", null, 100, null)).thenReturn(PageResponse.last(List.of()));

        new FeedProductionAlertGenerator(feedingService, productionService, lotService, alertsService)
                .generate(today);

        verify(alertsService).raise(eq(tenantId), eq(farmId), eq("LOW_STOCK"), eq("IMPORTANT"),
                any(), any(), eq(today), eq("feed"), eq(feedId));
    }

    @Test
    void herdSalesGeneratorRaisesPaymentOverdue() {
        UUID saleId = UUID.randomUUID();
        when(lotService.list("OPEN", null, 100, null)).thenReturn(PageResponse.last(List.of()));
        when(farmService.list()).thenReturn(List.of());
        Instant soldAt = today.minus(40, ChronoUnit.DAYS).atStartOfDay(ZoneOffset.UTC).toInstant();
        when(salesService.listSales("CONFIRMED", null, null)).thenReturn(List.of(new SalesDtos.SaleResponse(
                saleId, farmId, null, "S-00001", "ANIMAL", "CONFIRMED", soldAt, new BigDecimal("100000"),
                "XOF", "UNPAID", null, null, List.of())));

        new HerdSalesAlertGenerator(lotService, productionService, siteService, salesService, farmService,
                alertsService).generate(today);

        verify(alertsService).raise(eq(tenantId), eq(farmId), eq("PAYMENT_OVERDUE"), eq("IMPORTANT"),
                any(), any(), eq(today.minus(10, ChronoUnit.DAYS)), eq("sale"), eq(saleId));
    }
}
