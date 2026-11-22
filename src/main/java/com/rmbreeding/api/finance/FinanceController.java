package com.rmbreeding.api.finance;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','ACCOUNTANT','PLATFORM_ADMIN')")
public class FinanceController {

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    // P10-13 Expenses

    @GetMapping("/expenses")
    public List<FinanceDtos.ExpenseResponse> listExpenses(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return financeService.listExpenses(from, to);
    }

    @PostMapping("/expenses")
    public ResponseEntity<FinanceDtos.ExpenseResponse> createExpense(
            @Valid @RequestBody FinanceDtos.ExpenseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(financeService.createExpense(request));
    }

    // P10-14 Revenues

    @GetMapping("/revenues")
    public List<FinanceDtos.RevenueResponse> listRevenues(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return financeService.listRevenues(from, to);
    }

    @PostMapping("/revenues")
    public ResponseEntity<FinanceDtos.RevenueResponse> createRevenue(
            @Valid @RequestBody FinanceDtos.RevenueCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(financeService.createRevenue(request));
    }

    // P10-15 Cost allocations

    @GetMapping("/lots/{lotId}/cost-allocations")
    public List<FinanceDtos.CostAllocationResponse> listCostAllocations(@PathVariable UUID lotId) {
        return financeService.listCostAllocations(lotId);
    }

    @PostMapping("/lots/{lotId}/cost-allocations")
    public ResponseEntity<FinanceDtos.CostAllocationResponse> createCostAllocation(@PathVariable UUID lotId,
            @Valid @RequestBody FinanceDtos.CostAllocationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(financeService.createCostAllocation(lotId, request));
    }

    // P10-16 Lot cost

    @GetMapping("/lots/{lotId}/cost")
    public FinanceDtos.LotCostResponse lotCost(@PathVariable UUID lotId) {
        return financeService.lotCost(lotId);
    }

    // P10-17 Lot margin

    @GetMapping("/lots/{lotId}/margin")
    public FinanceDtos.LotMarginResponse lotMargin(@PathVariable UUID lotId) {
        return financeService.lotMargin(lotId);
    }

    // P10-18 Financial statements

    @GetMapping("/financial-statements")
    public FinanceDtos.FinancialStatementResponse financialStatement(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return financeService.financialStatement(from, to);
    }
}
