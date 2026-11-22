package com.rmbreeding.api.finance;

import com.rmbreeding.api.feeding.FeedingService;
import com.rmbreeding.api.herd.LotDtos;
import com.rmbreeding.api.herd.LotService;
import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.identity.FarmService;
import com.rmbreeding.api.identity.IdentityDtos;
import com.rmbreeding.api.sales.SalesService;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private RevenueRepository revenueRepository;
    @Mock
    private CostAllocationRepository costAllocationRepository;
    @Mock
    private SalesService salesService;
    @Mock
    private LotService lotService;
    @Mock
    private FeedingService feedingService;
    @Mock
    private FarmService farmService;
    @Mock
    private AuditService auditService;

    private FinanceService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID farmId = UUID.randomUUID();
    private final UUID speciesId = UUID.randomUUID();
    private final UUID lotId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FinanceService(expenseRepository, revenueRepository, costAllocationRepository,
                salesService, lotService, feedingService, farmService, auditService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // P10-13

    @Test
    void createsExpenseWithDefaultFarm() {
        when(farmService.list()).thenReturn(List.of(farm()));

        FinanceDtos.ExpenseResponse response = service.createExpense(new FinanceDtos.ExpenseCreateRequest(
                "VETERINARY", new BigDecimal("25000"), "XOF", LocalDate.parse("2026-03-01"), "Vaccin", "Clinique", null));

        assertThat(response.farmId()).isEqualTo(farmId);
        assertThat(response.currency()).isEqualTo("XOF");
        verify(expenseRepository).save(any(ExpenseEntity.class));
        verify(auditService).record(eq("EXPENSE_CREATED"), eq("expense"), any());
    }

    @Test
    void createsExpenseAndAllocatesToLot() {
        when(lotService.get(lotId)).thenReturn(lot());

        service.createExpense(new FinanceDtos.ExpenseCreateRequest(
                "FEED", new BigDecimal("40000"), null, LocalDate.parse("2026-03-01"), null, null, lotId));

        verify(costAllocationRepository).save(any(CostAllocationEntity.class));
        verify(auditService).record(eq("EXPENSE_CREATED"), eq("expense"), any());
    }

    @Test
    void listsExpensesWithRange() {
        when(expenseRepository.findByTenantIdAndIncurredAtBetweenOrderByIncurredAtDesc(tenantId,
                LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31"))).thenReturn(List.of());

        assertThat(service.listExpenses(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31"))).isEmpty();
    }

    // P10-14

    @Test
    void createsRevenue() {
        when(farmService.list()).thenReturn(List.of(farm()));

        FinanceDtos.RevenueResponse response = service.createRevenue(new FinanceDtos.RevenueCreateRequest(
                "MILK", new BigDecimal("80000"), null, LocalDate.parse("2026-03-02"), "Vente lait"));

        assertThat(response.amount()).isEqualByComparingTo("80000");
        verify(revenueRepository).save(any(RevenueEntity.class));
        verify(auditService).record(eq("REVENUE_CREATED"), eq("revenue"), any());
    }

    // P10-15

    @Test
    void createsCostAllocation() {
        when(lotService.get(lotId)).thenReturn(lot());

        FinanceDtos.CostAllocationResponse response = service.createCostAllocation(lotId,
                new FinanceDtos.CostAllocationCreateRequest("MANUAL", null, "2026-03", new BigDecimal("10000"), "HEADCOUNT"));

        assertThat(response.lotId()).isEqualTo(lotId);
        assertThat(response.amount()).isEqualByComparingTo("10000");
        verify(costAllocationRepository).save(any(CostAllocationEntity.class));
    }

    @Test
    void listsCostAllocationsForLot() {
        when(costAllocationRepository.findByTenantIdAndLotIdOrderByAllocatedAtDesc(tenantId, lotId))
                .thenReturn(List.of());

        assertThat(service.listCostAllocations(lotId)).isEmpty();
    }

    // P10-16

    @Test
    void computesLotCost() {
        when(lotService.get(lotId)).thenReturn(lot());
        when(feedingService.feedCostForLot(lotId)).thenReturn(new BigDecimal("100000"));
        when(costAllocationRepository.findByTenantIdAndLotIdOrderByAllocatedAtDesc(tenantId, lotId))
                .thenReturn(List.of(allocation(new BigDecimal("20000")), allocation(new BigDecimal("5000"))));

        FinanceDtos.LotCostResponse response = service.lotCost(lotId);

        assertThat(response.feedCost()).isEqualByComparingTo("100000");
        assertThat(response.allocatedCost()).isEqualByComparingTo("25000");
        assertThat(response.totalCost()).isEqualByComparingTo("125000");
    }

    // P10-17

    @Test
    void computesLotMargin() {
        when(lotService.get(lotId)).thenReturn(lot());
        when(feedingService.feedCostForLot(lotId)).thenReturn(new BigDecimal("50000"));
        when(costAllocationRepository.findByTenantIdAndLotIdOrderByAllocatedAtDesc(tenantId, lotId))
                .thenReturn(List.of());
        when(salesService.revenueForLot(lotId)).thenReturn(new BigDecimal("200000"));

        FinanceDtos.LotMarginResponse response = service.lotMargin(lotId);

        assertThat(response.revenue()).isEqualByComparingTo("200000");
        assertThat(response.cost()).isEqualByComparingTo("50000");
        assertThat(response.margin()).isEqualByComparingTo("150000");
        assertThat(response.marginRate()).isEqualByComparingTo("75.00");
    }

    @Test
    void lotMarginRateIsNullWithoutRevenue() {
        when(lotService.get(lotId)).thenReturn(lot());
        when(feedingService.feedCostForLot(lotId)).thenReturn(new BigDecimal("50000"));
        when(costAllocationRepository.findByTenantIdAndLotIdOrderByAllocatedAtDesc(tenantId, lotId))
                .thenReturn(List.of());
        when(salesService.revenueForLot(lotId)).thenReturn(BigDecimal.ZERO);

        assertThat(service.lotMargin(lotId).marginRate()).isNull();
    }

    // P10-18

    @Test
    void buildsFinancialStatement() {
        when(revenueRepository.findByTenantIdAndReceivedAtBetweenOrderByReceivedAtDesc(tenantId,
                LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31")))
                .thenReturn(List.of(revenue("MILK", "80000"), revenue("MILK", "20000")));
        when(expenseRepository.findByTenantIdAndIncurredAtBetweenOrderByIncurredAtDesc(tenantId,
                LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31")))
                .thenReturn(List.of(expense("FEED", "30000")));

        FinanceDtos.FinancialStatementResponse response = service.financialStatement(
                LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31"));

        assertThat(response.totalRevenue()).isEqualByComparingTo("100000");
        assertThat(response.totalExpense()).isEqualByComparingTo("30000");
        assertThat(response.net()).isEqualByComparingTo("70000");
        assertThat(response.revenues()).hasSize(1);
        assertThat(response.revenues().get(0).amount()).isEqualByComparingTo("100000");
        assertThat(response.expenses()).hasSize(1);
    }

    private IdentityDtos.FarmResponse farm() {
        return new IdentityDtos.FarmResponse(farmId, tenantId, "Ferme", "F1", null, null, null);
    }

    private LotDtos.LotResponse lot() {
        return new LotDtos.LotResponse(lotId, farmId, speciesId, "Bande 1", null, null, "OPEN",
                LocalDate.parse("2026-01-01"), 100, 100, null, null, null, 1, Instant.now(), Instant.now());
    }

    private CostAllocationEntity allocation(BigDecimal amount) {
        CostAllocationEntity allocation = new CostAllocationEntity();
        allocation.id = UUID.randomUUID();
        allocation.tenantId = tenantId;
        allocation.lotId = lotId;
        allocation.amount = amount;
        return allocation;
    }

    private ExpenseEntity expense(String category, String amount) {
        ExpenseEntity expense = new ExpenseEntity();
        expense.id = UUID.randomUUID();
        expense.tenantId = tenantId;
        expense.farmId = farmId;
        expense.category = category;
        expense.amount = new BigDecimal(amount);
        expense.incurredAt = LocalDate.parse("2026-03-05");
        return expense;
    }

    private RevenueEntity revenue(String category, String amount) {
        RevenueEntity revenue = new RevenueEntity();
        revenue.id = UUID.randomUUID();
        revenue.tenantId = tenantId;
        revenue.farmId = farmId;
        revenue.category = category;
        revenue.amount = new BigDecimal(amount);
        revenue.receivedAt = LocalDate.parse("2026-03-05");
        return revenue;
    }
}
