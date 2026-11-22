package com.rmbreeding.api.sales;

import com.rmbreeding.api.health.HealthService;
import com.rmbreeding.api.herd.AnimalService;
import com.rmbreeding.api.herd.HerdDtos;
import com.rmbreeding.api.herd.LotDtos;
import com.rmbreeding.api.herd.LotService;
import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.identity.FarmService;
import com.rmbreeding.api.identity.IdentityDtos;
import com.rmbreeding.api.production.ProductionDtos;
import com.rmbreeding.api.production.ProductionService;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SalesService {

    private static final Duration CANCELLATION_WINDOW = Duration.ofHours(48);

    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final SaleLineRepository saleLineRepository;
    private final PaymentRepository paymentRepository;
    private final AnimalService animalService;
    private final LotService lotService;
    private final HealthService healthService;
    private final ProductionService productionService;
    private final FarmService farmService;
    private final AuditService auditService;

    public SalesService(CustomerRepository customerRepository,
            SaleRepository saleRepository,
            SaleLineRepository saleLineRepository,
            PaymentRepository paymentRepository,
            AnimalService animalService,
            LotService lotService,
            HealthService healthService,
            ProductionService productionService,
            FarmService farmService,
            AuditService auditService) {
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
        this.saleLineRepository = saleLineRepository;
        this.paymentRepository = paymentRepository;
        this.animalService = animalService;
        this.lotService = lotService;
        this.healthService = healthService;
        this.productionService = productionService;
        this.farmService = farmService;
        this.auditService = auditService;
    }

    // P10-01 Customers

    public List<SalesDtos.CustomerResponse> listCustomers(boolean includeArchived) {
        UUID tenantId = TenantContext.requireTenantId();
        List<CustomerEntity> customers = includeArchived
                ? customerRepository.findByTenantIdOrderByName(tenantId)
                : customerRepository.findByTenantIdAndArchivedAtIsNullOrderByName(tenantId);
        return customers.stream().map(this::toCustomerResponse).toList();
    }

    @Transactional
    public SalesDtos.CustomerResponse createCustomer(SalesDtos.CustomerCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        CustomerEntity customer = new CustomerEntity();
        customer.id = UUID.randomUUID();
        customer.tenantId = tenantId;
        customer.farmId = requireDefaultFarmId();
        customer.name = request.name();
        customer.phone = request.phone();
        customer.email = request.email();
        customer.address = request.address();
        customer.balance = BigDecimal.ZERO;
        customerRepository.save(customer);
        auditService.record("CUSTOMER_CREATED", "customer", customer.id);
        return toCustomerResponse(customer);
    }

    public SalesDtos.CustomerResponse getCustomer(UUID customerId) {
        return toCustomerResponse(requireCustomer(customerId));
    }

    @Transactional
    public SalesDtos.CustomerResponse patchCustomer(UUID customerId, SalesDtos.CustomerPatchRequest request) {
        CustomerEntity customer = requireCustomer(customerId);
        if (request.name() != null) {
            customer.name = request.name();
        }
        if (request.phone() != null) {
            customer.phone = request.phone();
        }
        if (request.email() != null) {
            customer.email = request.email();
        }
        if (request.address() != null) {
            customer.address = request.address();
        }
        customerRepository.save(customer);
        auditService.record("CUSTOMER_UPDATED", "customer", customer.id);
        return toCustomerResponse(customer);
    }

    @Transactional
    public void archiveCustomer(UUID customerId) {
        CustomerEntity customer = requireCustomer(customerId);
        if (customer.archivedAt == null) {
            customer.archivedAt = Instant.now();
            customerRepository.save(customer);
            auditService.record("CUSTOMER_ARCHIVED", "customer", customer.id);
        }
    }

    // P10-02..P10-05 Sales

    public List<SalesDtos.SaleResponse> listSales(String status, Instant from, Instant to) {
        UUID tenantId = TenantContext.requireTenantId();
        List<SaleEntity> sales;
        if (from != null && to != null) {
            sales = saleRepository.findByTenantIdAndSoldAtBetweenOrderBySoldAtDesc(tenantId, from, to);
        } else if (status != null && !status.isBlank()) {
            sales = saleRepository.findByTenantIdAndStatusOrderBySoldAtDesc(tenantId, status);
        } else {
            sales = saleRepository.findByTenantIdOrderBySoldAtDesc(tenantId);
        }
        return sales.stream().map(this::toSaleResponse).toList();
    }

    public SalesDtos.SaleResponse getSale(UUID saleId) {
        return toSaleResponse(requireSale(saleId));
    }

    @Transactional
    public SalesDtos.SaleResponse createSale(SalesDtos.SaleCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        if (request.customerId() != null) {
            requireCustomer(request.customerId());
        }
        UUID farmId = resolveFarmId(request);
        SaleEntity sale = new SaleEntity();
        sale.id = UUID.randomUUID();
        sale.tenantId = tenantId;
        sale.farmId = farmId;
        sale.customerId = request.customerId();
        sale.number = nextNumber(tenantId);
        sale.type = request.type();
        sale.status = "CONFIRMED";
        sale.soldAt = request.soldAt();
        sale.currency = request.currency() == null ? "XOF" : request.currency();
        sale.notes = request.notes();
        sale.cancellationDeadline = request.soldAt().plus(CANCELLATION_WINDOW);
        sale.totalAmount = BigDecimal.ZERO;
        saleRepository.save(sale);

        BigDecimal total = BigDecimal.ZERO;
        for (SalesDtos.SaleLineRequest line : request.lines()) {
            String itemType = line.itemType() == null ? request.type() : line.itemType();
            BigDecimal amount = line.quantity().multiply(line.unitPrice());
            validateLine(tenantId, sale, itemType, line);
            SaleLineEntity entity = new SaleLineEntity();
            entity.id = UUID.randomUUID();
            entity.tenantId = tenantId;
            entity.saleId = sale.id;
            entity.itemType = itemType;
            entity.animalId = line.animalId();
            entity.lotId = line.lotId();
            entity.description = line.description();
            entity.quantity = line.quantity();
            entity.unitPrice = line.unitPrice();
            entity.amount = amount;
            saleLineRepository.save(entity);
            total = total.add(amount);
        }
        sale.totalAmount = total;
        saleRepository.save(sale);
        auditService.record("SALE_CREATED", "sale", sale.id);
        return toSaleResponse(sale);
    }

    @Transactional
    public SalesDtos.SaleResponse cancelSale(UUID saleId, SalesDtos.SaleCancelRequest request) {
        SaleEntity sale = requireSale(saleId);
        if ("CANCELLED".equals(sale.status)) {
            throw new ConflictException("Sale is already cancelled");
        }
        if (sale.cancellationDeadline != null && Instant.now().isAfter(sale.cancellationDeadline)) {
            throw new ConflictException("Cancellation deadline has passed");
        }
        sale.status = "CANCELLED";
        sale.notes = (sale.notes == null ? "" : sale.notes + " ")
                + "[Annulée: " + request.reason() + "]";
        saleRepository.save(sale);
        auditService.record("SALE_CANCELLED", "sale", sale.id);
        return toSaleResponse(sale);
    }

    // P10-06..P10-07 Payments

    @Transactional
    public SalesDtos.PaymentResponse addPayment(UUID saleId, SalesDtos.PaymentCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        SaleEntity sale = requireSale(saleId);
        if ("CANCELLED".equals(sale.status)) {
            throw new ConflictException("Cannot pay a cancelled sale");
        }
        PaymentEntity payment = new PaymentEntity();
        payment.id = UUID.randomUUID();
        payment.tenantId = tenantId;
        payment.saleId = sale.id;
        payment.amount = request.amount();
        payment.method = request.method();
        payment.status = "COMPLETED";
        payment.paidAt = request.paidAt();
        payment.reference = request.reference();
        paymentRepository.save(payment);
        auditService.record("PAYMENT_RECORDED", "payment", payment.id);
        return toPaymentResponse(payment);
    }

    public List<SalesDtos.PaymentResponse> listPayments(UUID saleId) {
        UUID tenantId = TenantContext.requireTenantId();
        requireSale(saleId);
        return paymentRepository.findByTenantIdAndSaleIdOrderByPaidAtAsc(tenantId, saleId).stream()
                .map(this::toPaymentResponse).toList();
    }

    public BigDecimal revenueForLot(UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        return saleLineRepository.findByTenantIdAndLotId(tenantId, lotId).stream()
                .filter(line -> !"CANCELLED".equals(requireSaleStatus(line.saleId)))
                .map(line -> line.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String requireSaleStatus(UUID saleId) {
        UUID tenantId = TenantContext.requireTenantId();
        return saleRepository.findByIdAndTenantId(saleId, tenantId)
                .map(sale -> sale.status)
                .orElse("CANCELLED");
    }

    // Helpers

    private void validateLine(UUID tenantId, SaleEntity sale, String itemType, SalesDtos.SaleLineRequest line) {
        if ("ANIMAL".equals(itemType)) {
            if (line.animalId() == null) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "An animal line requires animalId");
            }
            if (line.quantity().stripTrailingZeros().compareTo(BigDecimal.ONE) != 0) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "An animal line quantity must be 1");
            }
            animalService.get(line.animalId());
            healthService.assertSaleAllowed(line.animalId(), null, withdrawalType(sale.type),
                    toLocalDate(sale.soldAt));
        } else if ("LOT".equals(itemType)) {
            if (line.lotId() == null) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A lot line requires lotId");
            }
            LotDtos.LotResponse lot = lotService.get(line.lotId());
            if (line.quantity().compareTo(BigDecimal.valueOf(lot.currentCount())) > 0) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Sale quantity exceeds lot headcount");
            }
            healthService.assertSaleAllowed(null, line.lotId(), withdrawalType(sale.type),
                    toLocalDate(sale.soldAt));
        } else if ("EGGS".equals(itemType)) {
            if (line.lotId() == null) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "An egg line requires lotId");
            }
            lotService.get(line.lotId());
            productionService.createMovement(new ProductionDtos.EggMovementCreateRequest(
                    line.lotId(), "SALE", line.quantity().intValueExact(), sale.soldAt, "Vente " + sale.number));
        }
    }

    /**
     * Le type de vente (ANIMAL, LOT, EGGS, OTHER) n'est pas le type de délai d'attente
     * (MEAT, EGGS, MILK) : les ventes d'animaux ou de lots contrôlent le délai viande.
     */
    private static String withdrawalType(String saleType) {
        return "EGGS".equals(saleType) ? "EGGS" : "MEAT";
    }

    private UUID resolveFarmId(SalesDtos.SaleCreateRequest request) {
        for (SalesDtos.SaleLineRequest line : request.lines()) {
            if (line.animalId() != null) {
                return animalService.get(line.animalId()).farmId();
            }
            if (line.lotId() != null) {
                return lotService.get(line.lotId()).farmId();
            }
        }
        if (request.customerId() != null) {
            return requireCustomer(request.customerId()).farmId;
        }
        return requireDefaultFarmId();
    }

    private UUID requireDefaultFarmId() {
        List<IdentityDtos.FarmResponse> farms = farmService.list();
        if (farms.isEmpty()) {
            throw new NotFoundException("No farm available for this tenant");
        }
        return farms.get(0).id();
    }

    private String nextNumber(UUID tenantId) {
        long next = saleRepository.countByTenantId(tenantId) + 1;
        return "S-" + String.format("%05d", next);
    }

    private java.time.LocalDate toLocalDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private CustomerEntity requireCustomer(UUID customerId) {
        UUID tenantId = TenantContext.requireTenantId();
        return customerRepository.findByIdAndTenantId(customerId, tenantId)
                .orElseThrow(() -> new NotFoundException("Customer not found"));
    }

    private SaleEntity requireSale(UUID saleId) {
        UUID tenantId = TenantContext.requireTenantId();
        return saleRepository.findByIdAndTenantId(saleId, tenantId)
                .orElseThrow(() -> new NotFoundException("Sale not found"));
    }

    private String paymentStatus(SaleEntity sale) {
        UUID tenantId = TenantContext.requireTenantId();
        BigDecimal paid = paymentRepository.findByTenantIdAndSaleIdOrderByPaidAtAsc(tenantId, sale.id).stream()
                .filter(payment -> !"CANCELLED".equals(payment.status))
                .map(payment -> payment.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return "UNPAID";
        }
        return paid.compareTo(sale.totalAmount) >= 0 ? "PAID" : "PARTIAL";
    }

    private SalesDtos.CustomerResponse toCustomerResponse(CustomerEntity customer) {
        return new SalesDtos.CustomerResponse(customer.id, customer.farmId, customer.name, customer.phone,
                customer.email, customer.address, customer.balance, customer.archivedAt);
    }

    private SalesDtos.SaleResponse toSaleResponse(SaleEntity sale) {
        UUID tenantId = TenantContext.requireTenantId();
        List<SalesDtos.SaleLineResponse> lines = saleLineRepository
                .findByTenantIdAndSaleId(tenantId, sale.id).stream()
                .map(line -> new SalesDtos.SaleLineResponse(line.id, line.itemType, line.animalId, line.lotId,
                        line.description, line.quantity, line.unitPrice, line.amount))
                .toList();
        return new SalesDtos.SaleResponse(sale.id, sale.farmId, sale.customerId, sale.number, sale.type,
                sale.status, sale.soldAt, sale.totalAmount, sale.currency, paymentStatus(sale), sale.notes,
                sale.cancellationDeadline, lines);
    }

    private SalesDtos.PaymentResponse toPaymentResponse(PaymentEntity payment) {
        return new SalesDtos.PaymentResponse(payment.id, payment.saleId, payment.amount, payment.method,
                payment.status, payment.paidAt, payment.reference);
    }
}
