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
import com.rmbreeding.api.shared.NotFoundException;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private SaleRepository saleRepository;
    @Mock
    private SaleLineRepository saleLineRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private AnimalService animalService;
    @Mock
    private LotService lotService;
    @Mock
    private HealthService healthService;
    @Mock
    private ProductionService productionService;
    @Mock
    private FarmService farmService;
    @Mock
    private AuditService auditService;

    private SalesService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID farmId = UUID.randomUUID();
    private final UUID speciesId = UUID.randomUUID();
    private final UUID animalId = UUID.randomUUID();
    private final UUID lotId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SalesService(customerRepository, saleRepository, saleLineRepository, paymentRepository,
                animalService, lotService, healthService, productionService, farmService, auditService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // P10-01

    @Test
    void listsActiveCustomers() {
        when(customerRepository.findByTenantIdAndArchivedAtIsNullOrderByName(tenantId))
                .thenReturn(List.of(customer()));

        List<SalesDtos.CustomerResponse> customers = service.listCustomers(false);

        assertThat(customers).hasSize(1);
        assertThat(customers.get(0).name()).isEqualTo("Kouassi");
    }

    @Test
    void createsCustomerWithDefaultFarm() {
        when(farmService.list()).thenReturn(List.of(farm()));

        SalesDtos.CustomerResponse response = service.createCustomer(
                new SalesDtos.CustomerCreateRequest("Kouassi", "0700", "k@example.com", "Abidjan"));

        assertThat(response.id()).isNotNull();
        assertThat(response.farmId()).isEqualTo(farmId);
        assertThat(response.balance()).isEqualByComparingTo("0");
        verify(customerRepository).save(any(CustomerEntity.class));
        verify(auditService).record(eq("CUSTOMER_CREATED"), eq("customer"), any());
    }

    @Test
    void rejectsUnknownCustomer() {
        when(customerRepository.findByIdAndTenantId(customerId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCustomer(customerId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void patchesCustomerFields() {
        CustomerEntity entity = customer();
        when(customerRepository.findByIdAndTenantId(customerId, tenantId)).thenReturn(Optional.of(entity));

        SalesDtos.CustomerResponse response = service.patchCustomer(customerId,
                new SalesDtos.CustomerPatchRequest("Nouveau", null, null, null));

        assertThat(response.name()).isEqualTo("Nouveau");
        assertThat(response.phone()).isEqualTo("0700");
        verify(auditService).record(eq("CUSTOMER_UPDATED"), eq("customer"), any());
    }

    @Test
    void archivesCustomer() {
        CustomerEntity entity = customer();
        when(customerRepository.findByIdAndTenantId(customerId, tenantId)).thenReturn(Optional.of(entity));

        service.archiveCustomer(customerId);

        assertThat(entity.archivedAt).isNotNull();
        verify(auditService).record(eq("CUSTOMER_ARCHIVED"), eq("customer"), any());
    }

    // P10-02..P10-05

    @Test
    void createsAnimalSaleWithNumberAndTotal() {
        when(customerRepository.findByIdAndTenantId(customerId, tenantId)).thenReturn(Optional.of(customer()));
        when(animalService.get(animalId)).thenReturn(animal());
        when(saleRepository.countByTenantId(tenantId)).thenReturn(0L);

        SalesDtos.SaleResponse response = service.createSale(new SalesDtos.SaleCreateRequest(
                customerId, "ANIMAL", Instant.parse("2026-03-01T10:00:00Z"), "XOF", null,
                List.of(new SalesDtos.SaleLineRequest("ANIMAL", animalId, null, null,
                        BigDecimal.ONE, new BigDecimal("150000")))));

        assertThat(response.number()).isEqualTo("S-00001");
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.totalAmount()).isEqualByComparingTo("150000");
        assertThat(response.paymentStatus()).isEqualTo("UNPAID");
        verify(saleRepository, org.mockito.Mockito.times(2)).save(any(SaleEntity.class));
        verify(saleLineRepository).save(any(SaleLineEntity.class));
        verify(healthService).assertSaleAllowed(eq(animalId), isNull(), eq("MEAT"), any(LocalDate.class));
        verify(auditService).record(eq("SALE_CREATED"), eq("sale"), any());
    }

    @Test
    void rejectsAnimalLineWithQuantityOtherThanOne() {
        when(animalService.get(animalId)).thenReturn(animal());

        assertThatThrownBy(() -> service.createSale(new SalesDtos.SaleCreateRequest(
                null, "ANIMAL", Instant.parse("2026-03-01T10:00:00Z"), null, null,
                List.of(new SalesDtos.SaleLineRequest("ANIMAL", animalId, null, null,
                        new BigDecimal("2"), new BigDecimal("150000"))))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsLotLineExceedingHeadcount() {
        when(lotService.get(lotId)).thenReturn(lot());

        assertThatThrownBy(() -> service.createSale(new SalesDtos.SaleCreateRequest(
                null, "LOT", Instant.parse("2026-03-01T10:00:00Z"), null, null,
                List.of(new SalesDtos.SaleLineRequest("LOT", null, lotId, null,
                        new BigDecimal("101"), new BigDecimal("5000"))))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void createsEggLineAndDecrementsStock() {
        when(lotService.get(lotId)).thenReturn(lot());

        service.createSale(new SalesDtos.SaleCreateRequest(
                null, "EGGS", Instant.parse("2026-03-01T10:00:00Z"), null, null,
                List.of(new SalesDtos.SaleLineRequest("EGGS", null, lotId, null,
                        new BigDecimal("30"), new BigDecimal("150")))));

        verify(productionService).createMovement(any(ProductionDtos.EggMovementCreateRequest.class));
    }

    @Test
    void cancelsSaleWithinDeadline() {
        SaleEntity entity = sale("CONFIRMED", Instant.now().plusSeconds(3600));
        when(saleRepository.findByIdAndTenantId(entity.id, tenantId)).thenReturn(Optional.of(entity));

        SalesDtos.SaleResponse response = service.cancelSale(entity.id,
                new SalesDtos.SaleCancelRequest("Erreur de saisie"));

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.notes()).contains("Erreur de saisie");
        verify(auditService).record(eq("SALE_CANCELLED"), eq("sale"), any());
    }

    @Test
    void rejectsCancellingAlreadyCancelledSale() {
        SaleEntity entity = sale("CANCELLED", Instant.now().plusSeconds(3600));
        when(saleRepository.findByIdAndTenantId(entity.id, tenantId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.cancelSale(entity.id, new SalesDtos.SaleCancelRequest("x")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsCancellingAfterDeadline() {
        SaleEntity entity = sale("CONFIRMED", Instant.now().minusSeconds(3600));
        when(saleRepository.findByIdAndTenantId(entity.id, tenantId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.cancelSale(entity.id, new SalesDtos.SaleCancelRequest("x")))
                .isInstanceOf(ConflictException.class);
    }

    // P10-06..P10-07

    @Test
    void recordsPayment() {
        SaleEntity entity = sale("CONFIRMED", Instant.now().plusSeconds(3600));
        when(saleRepository.findByIdAndTenantId(entity.id, tenantId)).thenReturn(Optional.of(entity));

        SalesDtos.PaymentResponse response = service.addPayment(entity.id,
                new SalesDtos.PaymentCreateRequest(new BigDecimal("50000"), "MOBILE_MONEY",
                        Instant.parse("2026-03-01T11:00:00Z"), "REF-1"));

        assertThat(response.amount()).isEqualByComparingTo("50000");
        assertThat(response.method()).isEqualTo("MOBILE_MONEY");
        assertThat(response.status()).isEqualTo("COMPLETED");
        verify(paymentRepository).save(any(PaymentEntity.class));
        verify(auditService).record(eq("PAYMENT_RECORDED"), eq("payment"), any());
    }

    @Test
    void rejectsPaymentOnCancelledSale() {
        SaleEntity entity = sale("CANCELLED", Instant.now().plusSeconds(3600));
        when(saleRepository.findByIdAndTenantId(entity.id, tenantId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.addPayment(entity.id, new SalesDtos.PaymentCreateRequest(
                new BigDecimal("50000"), "CASH", Instant.parse("2026-03-01T11:00:00Z"), null)))
                .isInstanceOf(ConflictException.class);
        verify(paymentRepository, never()).save(any(PaymentEntity.class));
    }

    @Test
    void computesPartialPaymentStatus() {
        SaleEntity entity = sale("CONFIRMED", Instant.now().plusSeconds(3600));
        entity.totalAmount = new BigDecimal("100000");
        when(saleRepository.findByIdAndTenantId(entity.id, tenantId)).thenReturn(Optional.of(entity));
        PaymentEntity payment = new PaymentEntity();
        payment.id = UUID.randomUUID();
        payment.tenantId = tenantId;
        payment.saleId = entity.id;
        payment.amount = new BigDecimal("40000");
        payment.status = "COMPLETED";
        when(paymentRepository.findByTenantIdAndSaleIdOrderByPaidAtAsc(tenantId, entity.id))
                .thenReturn(List.of(payment));

        SalesDtos.SaleResponse response = service.getSale(entity.id);

        assertThat(response.paymentStatus()).isEqualTo("PARTIAL");
    }

    @Test
    void listsPayments() {
        SaleEntity entity = sale("CONFIRMED", Instant.now().plusSeconds(3600));
        when(saleRepository.findByIdAndTenantId(entity.id, tenantId)).thenReturn(Optional.of(entity));
        when(paymentRepository.findByTenantIdAndSaleIdOrderByPaidAtAsc(tenantId, entity.id))
                .thenReturn(List.of());

        assertThat(service.listPayments(entity.id)).isEmpty();
    }

    @Test
    void computesRevenueForLotExcludingCancelledSales() {
        SaleLineEntity confirmed = line(new BigDecimal("30000"));
        SaleLineEntity cancelled = line(new BigDecimal("20000"));
        SaleEntity confirmedSale = sale("CONFIRMED", Instant.now());
        SaleEntity cancelledSale = sale("CANCELLED", Instant.now());
        confirmed.saleId = confirmedSale.id;
        cancelled.saleId = cancelledSale.id;
        when(saleLineRepository.findByTenantIdAndLotId(tenantId, lotId))
                .thenReturn(List.of(confirmed, cancelled));
        when(saleRepository.findByIdAndTenantId(confirmedSale.id, tenantId)).thenReturn(Optional.of(confirmedSale));
        when(saleRepository.findByIdAndTenantId(cancelledSale.id, tenantId)).thenReturn(Optional.of(cancelledSale));

        assertThat(service.revenueForLot(lotId)).isEqualByComparingTo("30000");
    }

    private CustomerEntity customer() {
        CustomerEntity customer = new CustomerEntity();
        customer.id = customerId;
        customer.tenantId = tenantId;
        customer.farmId = farmId;
        customer.name = "Kouassi";
        customer.phone = "0700";
        customer.balance = BigDecimal.ZERO;
        return customer;
    }

    private SaleEntity sale(String status, Instant deadline) {
        SaleEntity sale = new SaleEntity();
        sale.id = UUID.randomUUID();
        sale.tenantId = tenantId;
        sale.farmId = farmId;
        sale.customerId = customerId;
        sale.number = "S-00001";
        sale.type = "ANIMAL";
        sale.status = status;
        sale.soldAt = Instant.parse("2026-03-01T10:00:00Z");
        sale.totalAmount = new BigDecimal("150000");
        sale.currency = "XOF";
        sale.cancellationDeadline = deadline;
        return sale;
    }

    private SaleLineEntity line(BigDecimal amount) {
        SaleLineEntity line = new SaleLineEntity();
        line.id = UUID.randomUUID();
        line.tenantId = tenantId;
        line.itemType = "LOT";
        line.lotId = lotId;
        line.quantity = BigDecimal.ONE;
        line.unitPrice = amount;
        line.amount = amount;
        return line;
    }

    private HerdDtos.AnimalResponse animal() {
        return new HerdDtos.AnimalResponse(animalId, farmId, speciesId, null, null, "A-001", "Bella", "FEMALE",
                LocalDate.parse("2026-01-15"), false, "NAISSANCE", "ACTIVE", null, null, null,
                new BigDecimal("30"), null, null, Instant.now(), Instant.now(), 1);
    }

    private LotDtos.LotResponse lot() {
        return new LotDtos.LotResponse(lotId, farmId, speciesId, "Bande 1", null, null, "OPEN",
                LocalDate.parse("2026-01-01"), 100, 100, null, null, null, 1, Instant.now(), Instant.now());
    }

    private IdentityDtos.FarmResponse farm() {
        return new IdentityDtos.FarmResponse(farmId, tenantId, "Ferme", "F1", null, null, null);
    }
}
