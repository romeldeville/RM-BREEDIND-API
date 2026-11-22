package com.rmbreeding.api.sales;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','ACCOUNTANT','PLATFORM_ADMIN')")
public class SalesController {

    private final SalesService salesService;

    public SalesController(SalesService salesService) {
        this.salesService = salesService;
    }

    // P10-01 Customers

    @GetMapping("/customers")
    public List<SalesDtos.CustomerResponse> listCustomers(
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return salesService.listCustomers(includeArchived);
    }

    @PostMapping("/customers")
    public ResponseEntity<SalesDtos.CustomerResponse> createCustomer(
            @Valid @RequestBody SalesDtos.CustomerCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(salesService.createCustomer(request));
    }

    @GetMapping("/customers/{customerId}")
    public SalesDtos.CustomerResponse getCustomer(@PathVariable UUID customerId) {
        return salesService.getCustomer(customerId);
    }

    @PatchMapping("/customers/{customerId}")
    public SalesDtos.CustomerResponse patchCustomer(@PathVariable UUID customerId,
            @Valid @RequestBody SalesDtos.CustomerPatchRequest request) {
        return salesService.patchCustomer(customerId, request);
    }

    @DeleteMapping("/customers/{customerId}")
    public ResponseEntity<Void> archiveCustomer(@PathVariable UUID customerId) {
        salesService.archiveCustomer(customerId);
        return ResponseEntity.noContent().build();
    }

    // P10-02..P10-05 Sales

    @GetMapping("/sales")
    public List<SalesDtos.SaleResponse> listSales(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return salesService.listSales(status, from, to);
    }

    @PostMapping("/sales")
    public ResponseEntity<SalesDtos.SaleResponse> createSale(
            @Valid @RequestBody SalesDtos.SaleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(salesService.createSale(request));
    }

    @GetMapping("/sales/{saleId}")
    public SalesDtos.SaleResponse getSale(@PathVariable UUID saleId) {
        return salesService.getSale(saleId);
    }

    @PostMapping("/sales/{saleId}/cancel")
    public SalesDtos.SaleResponse cancelSale(@PathVariable UUID saleId,
            @Valid @RequestBody SalesDtos.SaleCancelRequest request) {
        return salesService.cancelSale(saleId, request);
    }

    // P10-06..P10-07 Payments

    @GetMapping("/sales/{saleId}/payments")
    public List<SalesDtos.PaymentResponse> listPayments(@PathVariable UUID saleId) {
        return salesService.listPayments(saleId);
    }

    @PostMapping("/sales/{saleId}/payments")
    public ResponseEntity<SalesDtos.PaymentResponse> addPayment(@PathVariable UUID saleId,
            @Valid @RequestBody SalesDtos.PaymentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(salesService.addPayment(saleId, request));
    }
}
