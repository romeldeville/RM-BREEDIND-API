package com.rmbreeding.api.finance;

import com.rmbreeding.api.feeding.FeedingService;
import com.rmbreeding.api.herd.LotService;
import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.identity.FarmService;
import com.rmbreeding.api.identity.IdentityDtos;
import com.rmbreeding.api.sales.SalesService;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FinanceService {

    private final ExpenseRepository expenseRepository;
    private final RevenueRepository revenueRepository;
    private final CostAllocationRepository costAllocationRepository;
    private final SalesService salesService;
    private final LotService lotService;
    private final FeedingService feedingService;
    private final FarmService farmService;
    private final AuditService auditService;

    public FinanceService(ExpenseRepository expenseRepository,
            RevenueRepository revenueRepository,
            CostAllocationRepository costAllocationRepository,
            SalesService salesService,
            LotService lotService,
            FeedingService feedingService,
            FarmService farmService,
            AuditService auditService) {
        this.expenseRepository = expenseRepository;
        this.revenueRepository = revenueRepository;
        this.costAllocationRepository = costAllocationRepository;
        this.salesService = salesService;
        this.lotService = lotService;
        this.feedingService = feedingService;
        this.farmService = farmService;
        this.auditService = auditService;
    }

    // P10-13 Expenses

    public List<FinanceDtos.ExpenseResponse> listExpenses(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.requireTenantId();
        List<ExpenseEntity> expenses = from != null && to != null
                ? expenseRepository.findByTenantIdAndIncurredAtBetweenOrderByIncurredAtDesc(tenantId, from, to)
                : expenseRepository.findByTenantIdOrderByIncurredAtDesc(tenantId);
        return expenses.stream().map(this::toExpenseResponse).toList();
    }

    @Transactional
    public FinanceDtos.ExpenseResponse createExpense(FinanceDtos.ExpenseCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID farmId;
        if (request.lotId() != null) {
            farmId = lotService.get(request.lotId()).farmId();
        } else {
            farmId = requireDefaultFarmId();
        }
        ExpenseEntity expense = new ExpenseEntity();
        expense.id = UUID.randomUUID();
        expense.tenantId = tenantId;
        expense.farmId = farmId;
        expense.category = request.category();
        expense.amount = request.amount();
        expense.currency = request.currency() == null ? "XOF" : request.currency();
        expense.incurredAt = request.incurredAt();
        expense.description = request.description();
        expense.supplier = request.supplier();
        expenseRepository.save(expense);
        if (request.lotId() != null) {
            allocate(tenantId, request.lotId(), "EXPENSE", expense.id, null, request.amount(), "MANUAL");
        }
        auditService.record("EXPENSE_CREATED", "expense", expense.id);
        return toExpenseResponse(expense);
    }

    // P10-14 Revenues

    public List<FinanceDtos.RevenueResponse> listRevenues(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.requireTenantId();
        List<RevenueEntity> revenues = from != null && to != null
                ? revenueRepository.findByTenantIdAndReceivedAtBetweenOrderByReceivedAtDesc(tenantId, from, to)
                : revenueRepository.findByTenantIdOrderByReceivedAtDesc(tenantId);
        return revenues.stream().map(this::toRevenueResponse).toList();
    }

    @Transactional
    public FinanceDtos.RevenueResponse createRevenue(FinanceDtos.RevenueCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        RevenueEntity revenue = new RevenueEntity();
        revenue.id = UUID.randomUUID();
        revenue.tenantId = tenantId;
        revenue.farmId = requireDefaultFarmId();
        revenue.category = request.category();
        revenue.amount = request.amount();
        revenue.currency = request.currency() == null ? "XOF" : request.currency();
        revenue.receivedAt = request.receivedAt();
        revenue.description = request.description();
        revenueRepository.save(revenue);
        auditService.record("REVENUE_CREATED", "revenue", revenue.id);
        return toRevenueResponse(revenue);
    }

    // P10-15 Cost allocations

    public List<FinanceDtos.CostAllocationResponse> listCostAllocations(UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        List<CostAllocationEntity> allocations = lotId == null
                ? costAllocationRepository.findByTenantIdOrderByAllocatedAtDesc(tenantId)
                : costAllocationRepository.findByTenantIdAndLotIdOrderByAllocatedAtDesc(tenantId, lotId);
        return allocations.stream().map(this::toAllocationResponse).toList();
    }

    @Transactional
    public FinanceDtos.CostAllocationResponse createCostAllocation(UUID lotId,
            FinanceDtos.CostAllocationCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        lotService.get(lotId);
        CostAllocationEntity allocation = allocate(tenantId, lotId, request.sourceType(), request.sourceId(),
                request.period(), request.amount(), request.basis());
        auditService.record("COST_ALLOCATED", "cost_allocation", allocation.id);
        return toAllocationResponse(allocation);
    }

    // P10-16 Lot cost

    public FinanceDtos.LotCostResponse lotCost(UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        lotService.get(lotId);
        BigDecimal feedCost = feedingService.feedCostForLot(lotId);
        BigDecimal allocatedCost = costAllocationRepository
                .findByTenantIdAndLotIdOrderByAllocatedAtDesc(tenantId, lotId).stream()
                .map(allocation -> allocation.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinanceDtos.LotCostResponse(lotId, feedCost, allocatedCost,
                feedCost.add(allocatedCost));
    }

    // P10-17 Lot margin

    public FinanceDtos.LotMarginResponse lotMargin(UUID lotId) {
        BigDecimal cost = lotCost(lotId).totalCost();
        BigDecimal revenue = salesService.revenueForLot(lotId);
        BigDecimal margin = revenue.subtract(cost);
        BigDecimal marginRate = revenue.compareTo(BigDecimal.ZERO) > 0
                ? margin.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP)
                : null;
        return new FinanceDtos.LotMarginResponse(lotId, revenue, cost, margin, marginRate);
    }

    // P10-18 Financial statements

    public FinanceDtos.FinancialStatementResponse financialStatement(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.requireTenantId();
        LocalDate start = from == null ? LocalDate.of(1970, 1, 1) : from;
        LocalDate end = to == null ? LocalDate.now().plusDays(1) : to;
        Map<String, BigDecimal> revenueByCategory = new LinkedHashMap<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (RevenueEntity revenue : revenueRepository
                .findByTenantIdAndReceivedAtBetweenOrderByReceivedAtDesc(tenantId, start, end)) {
            revenueByCategory.merge(revenue.category, revenue.amount, BigDecimal::add);
            totalRevenue = totalRevenue.add(revenue.amount);
        }
        Map<String, BigDecimal> expenseByCategory = new LinkedHashMap<>();
        BigDecimal totalExpense = BigDecimal.ZERO;
        for (ExpenseEntity expense : expenseRepository
                .findByTenantIdAndIncurredAtBetweenOrderByIncurredAtDesc(tenantId, start, end)) {
            expenseByCategory.merge(expense.category, expense.amount, BigDecimal::add);
            totalExpense = totalExpense.add(expense.amount);
        }
        List<FinanceDtos.FinancialStatementLine> revenues = revenueByCategory.entrySet().stream()
                .map(entry -> new FinanceDtos.FinancialStatementLine(entry.getKey(), entry.getValue()))
                .toList();
        List<FinanceDtos.FinancialStatementLine> expenses = expenseByCategory.entrySet().stream()
                .map(entry -> new FinanceDtos.FinancialStatementLine(entry.getKey(), entry.getValue()))
                .toList();
        return new FinanceDtos.FinancialStatementResponse(start, end, totalRevenue, totalExpense,
                totalRevenue.subtract(totalExpense), revenues, expenses);
    }

    // Helpers

    private CostAllocationEntity allocate(UUID tenantId, UUID lotId, String sourceType, UUID sourceId,
            String period, BigDecimal amount, String basis) {
        CostAllocationEntity allocation = new CostAllocationEntity();
        allocation.id = UUID.randomUUID();
        allocation.tenantId = tenantId;
        allocation.lotId = lotId;
        allocation.sourceType = sourceType;
        allocation.sourceId = sourceId;
        allocation.period = period;
        allocation.amount = amount;
        allocation.basis = basis;
        allocation.allocatedAt = Instant.now();
        costAllocationRepository.save(allocation);
        return allocation;
    }

    private UUID requireDefaultFarmId() {
        List<IdentityDtos.FarmResponse> farms = farmService.list();
        if (farms.isEmpty()) {
            throw new NotFoundException("No farm available for this tenant");
        }
        return farms.get(0).id();
    }

    private FinanceDtos.ExpenseResponse toExpenseResponse(ExpenseEntity expense) {
        return new FinanceDtos.ExpenseResponse(expense.id, expense.farmId, expense.category, expense.amount,
                expense.currency, expense.incurredAt, expense.description, expense.supplier);
    }

    private FinanceDtos.RevenueResponse toRevenueResponse(RevenueEntity revenue) {
        return new FinanceDtos.RevenueResponse(revenue.id, revenue.farmId, revenue.category, revenue.amount,
                revenue.currency, revenue.receivedAt, revenue.description);
    }

    private FinanceDtos.CostAllocationResponse toAllocationResponse(CostAllocationEntity allocation) {
        return new FinanceDtos.CostAllocationResponse(allocation.id, allocation.lotId, allocation.sourceType,
                allocation.sourceId, allocation.period, allocation.amount, allocation.basis,
                allocation.allocatedAt);
    }
}
