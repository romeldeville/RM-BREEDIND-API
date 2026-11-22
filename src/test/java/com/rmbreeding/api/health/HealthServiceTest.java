package com.rmbreeding.api.health;

import com.rmbreeding.api.herd.AnimalService;
import com.rmbreeding.api.herd.HerdDtos;
import com.rmbreeding.api.herd.HerdLookup;
import com.rmbreeding.api.herd.LotDtos;
import com.rmbreeding.api.herd.LotService;
import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.referential.SpeciesService;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock
    private HealthProductRepository productRepository;
    @Mock
    private VaccinationRepository vaccinationRepository;
    @Mock
    private TreatmentRepository treatmentRepository;
    @Mock
    private TreatmentAdministrationRepository administrationRepository;
    @Mock
    private WithdrawalRepository withdrawalRepository;
    @Mock
    private HealthEventRepository healthEventRepository;
    @Mock
    private MortalityRepository mortalityRepository;
    @Mock
    private QuarantineRepository quarantineRepository;
    @Mock
    private HerdLookup herdLookup;
    @Mock
    private LotService lotService;
    @Mock
    private AnimalService animalService;
    @Mock
    private SpeciesService speciesService;
    @Mock
    private AuditService auditService;

    private HealthService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID farmId = UUID.randomUUID();
    private final UUID speciesId = UUID.randomUUID();
    private final UUID animalId = UUID.randomUUID();
    private final UUID lotId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new HealthService(productRepository, vaccinationRepository, treatmentRepository,
                administrationRepository, withdrawalRepository, healthEventRepository, mortalityRepository,
                quarantineRepository, herdLookup, lotService, animalService, speciesService, auditService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // P7-01

    @Test
    void createsProductWithWithdrawalDays() {
        HealthDtos.ProductResponse response = service.createProduct(productRequest(10, 0, 5));

        assertThat(response.name()).isEqualTo("Vax");
        assertThat(response.withdrawalMeatDays()).isEqualTo(10);
        assertThat(response.withdrawalMilkDays()).isEqualTo(5);
        verify(productRepository).save(any(HealthProductEntity.class));
        verify(auditService).record("HEALTH_PRODUCT_CREATED", "health_product", response.id());
    }

    @Test
    void patchesProductFields() {
        HealthProductEntity product = product(10, 0, 5);
        when(productRepository.findByIdAndTenantId(product.id, tenantId)).thenReturn(Optional.of(product));

        HealthDtos.ProductResponse response = service.patchProduct(product.id,
                new HealthDtos.ProductPatchRequest("New name", null, null, null, null, 20, null, null,
                        null, null, null));

        assertThat(response.name()).isEqualTo("New name");
        assertThat(response.withdrawalMeatDays()).isEqualTo(20);
        verify(auditService).record("HEALTH_PRODUCT_UPDATED", "health_product", product.id);
    }

    @Test
    void archivesProduct() {
        HealthProductEntity product = product(10, 0, 5);
        when(productRepository.findByIdAndTenantId(product.id, tenantId)).thenReturn(Optional.of(product));

        service.archiveProduct(product.id);

        assertThat(product.archivedAt).isNotNull();
        verify(auditService).record("HEALTH_PRODUCT_ARCHIVED", "health_product", product.id);
    }

    @Test
    void listsProducts() {
        HealthProductEntity product = product(10, 0, 5);
        when(productRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(product));

        List<HealthDtos.ProductResponse> response = service.listProducts("vax", null, false);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(product.id);
    }

    @Test
    void unknownProductIsNotFound() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findByIdAndTenantId(productId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProduct(productId)).isInstanceOf(NotFoundException.class);
    }

    // P7-03

    @Test
    void recordsVaccinationForAnimal() {
        when(herdLookup.requireAnimal(tenantId, animalId)).thenReturn(animal());
        HealthProductEntity product = product(0, 0, 0);
        when(productRepository.findByIdAndTenantId(product.id, tenantId)).thenReturn(Optional.of(product));

        HealthDtos.VaccinationResponse response = service.recordVaccination(
                new HealthDtos.VaccinationCreateRequest(animalId, null, product.id, null, "LOT-1",
                        "2ml", "IM", 1, Instant.parse("2026-03-01T10:00:00Z"), null, null));

        assertThat(response.animalId()).isEqualTo(animalId);
        assertThat(response.count()).isEqualTo(1);
        verify(vaccinationRepository).save(any(VaccinationEntity.class));
        verify(auditService).record("VACCINATION_RECORDED", "vaccination", response.id());
    }

    @Test
    void rejectsVaccinationWithoutTarget() {
        assertThatThrownBy(() -> service.recordVaccination(new HealthDtos.VaccinationCreateRequest(
                null, null, UUID.randomUUID(), null, null, null, null, 1,
                Instant.parse("2026-03-01T10:00:00Z"), null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("animal or a lot");
        verify(vaccinationRepository, never()).save(any(VaccinationEntity.class));
    }

    // P7-04 + P7-05

    @Test
    void createsTreatmentAndComputesWithdrawals() {
        when(herdLookup.requireAnimal(tenantId, animalId)).thenReturn(animal());
        HealthProductEntity product = product(10, 0, 5);
        when(productRepository.findByIdAndTenantId(product.id, tenantId)).thenReturn(Optional.of(product));
        when(withdrawalRepository.findByTenantIdAndTreatmentId(eq(tenantId), any()))
                .thenAnswer(invocation -> {
                    UUID treatmentId = invocation.getArgument(1);
                    return List.of(withdrawal(treatmentId, "MEAT", LocalDate.of(2026, 3, 15)),
                            withdrawal(treatmentId, "MILK", LocalDate.of(2026, 3, 10)));
                });

        HealthDtos.TreatmentResponse response = service.createTreatment(
                new HealthDtos.TreatmentCreateRequest(animalId, null, product.id, "Pneumonie", null,
                        null, null, LocalDate.of(2026, 3, 1), 4, null));

        assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 3, 5));
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.withdrawalEndDate()).isEqualTo(LocalDate.of(2026, 3, 15));

        ArgumentCaptor<List<WithdrawalEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(withdrawalRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).extracting(withdrawal -> withdrawal.type)
                .containsExactlyInAnyOrder("MEAT", "MILK");
        verify(auditService).record("TREATMENT_STARTED", "treatment", response.id());
    }

    @Test
    void createsNoWithdrawalWhenProductHasNoDelay() {
        when(herdLookup.requireAnimal(tenantId, animalId)).thenReturn(animal());
        HealthProductEntity product = product(0, 0, 0);
        when(productRepository.findByIdAndTenantId(product.id, tenantId)).thenReturn(Optional.of(product));

        service.createTreatment(new HealthDtos.TreatmentCreateRequest(animalId, null, product.id, null,
                null, null, null, LocalDate.of(2026, 3, 1), null, null));

        ArgumentCaptor<List<WithdrawalEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(withdrawalRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void addsAdministrationToActiveTreatment() {
        TreatmentEntity treatment = treatment("ACTIVE");
        when(treatmentRepository.findByIdAndTenantId(treatment.id, tenantId))
                .thenReturn(Optional.of(treatment));

        HealthDtos.AdministrationResponse response = service.addAdministration(treatment.id,
                new HealthDtos.AdministrationCreateRequest(Instant.parse("2026-03-02T08:00:00Z"), null));

        assertThat(response.treatmentId()).isEqualTo(treatment.id);
        verify(administrationRepository).save(any(TreatmentAdministrationEntity.class));
        verify(auditService).record("TREATMENT_ADMINISTERED", "treatment_administration", response.id());
    }

    @Test
    void rejectsAdministrationOnInactiveTreatment() {
        TreatmentEntity treatment = treatment("COMPLETED");
        when(treatmentRepository.findByIdAndTenantId(treatment.id, tenantId))
                .thenReturn(Optional.of(treatment));

        assertThatThrownBy(() -> service.addAdministration(treatment.id,
                new HealthDtos.AdministrationCreateRequest(Instant.parse("2026-03-02T08:00:00Z"), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not active");
    }

    // P7-06

    @Test
    void blocksSaleDuringWithdrawal() {
        WithdrawalEntity withdrawal = withdrawal(UUID.randomUUID(), "MEAT", LocalDate.of(2026, 3, 20));
        when(withdrawalRepository.findByTenantIdAndAnimalIdAndStatusAndEndDateGreaterThanEqual(
                tenantId, animalId, "ACTIVE", LocalDate.of(2026, 3, 10)))
                .thenReturn(List.of(withdrawal));

        HealthDtos.SaleEligibilityResponse response = service.checkSaleEligibility(animalId, null, "MEAT",
                LocalDate.of(2026, 3, 10));

        assertThat(response.allowed()).isFalse();
        assertThat(response.blockingWithdrawals()).hasSize(1);
    }

    @Test
    void allowsSaleWithoutWithdrawal() {
        when(withdrawalRepository.findByTenantIdAndAnimalIdAndStatusAndEndDateGreaterThanEqual(
                tenantId, animalId, "ACTIVE", LocalDate.of(2026, 3, 25)))
                .thenReturn(List.of());

        HealthDtos.SaleEligibilityResponse response = service.checkSaleEligibility(animalId, null, "MEAT",
                LocalDate.of(2026, 3, 25));

        assertThat(response.allowed()).isTrue();
    }

    @Test
    void assertSaleAllowedThrowsWhenBlocked() {
        when(withdrawalRepository.findByTenantIdAndAnimalIdAndStatusAndEndDateGreaterThanEqual(
                tenantId, animalId, "ACTIVE", LocalDate.of(2026, 3, 10)))
                .thenReturn(List.of(withdrawal(UUID.randomUUID(), "MEAT", LocalDate.of(2026, 3, 20))));

        assertThatThrownBy(() -> service.assertSaleAllowed(animalId, null, "MEAT", LocalDate.of(2026, 3, 10)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("withdrawal");
    }

    // P7-07

    @Test
    void recordsAnimalMortalityAndMarksAnimalDead() {
        when(herdLookup.requireAnimal(tenantId, animalId)).thenReturn(animal());

        HealthDtos.MortalityResponse response = service.recordMortality(
                new HealthDtos.MortalityCreateRequest(animalId, null, 1, "Maladie",
                        Instant.parse("2026-03-01T10:00:00Z")));

        assertThat(response.count()).isEqualTo(1);
        verify(animalService).changeStatus(eq(animalId), any(HerdDtos.AnimalStatusRequest.class));
        verify(mortalityRepository).save(any(MortalityEntity.class));
        verify(auditService).record("MORTALITY_RECORDED", "mortality", response.id());
    }

    @Test
    void recordsLotMortalityAndDecrementsHeadcount() {
        when(lotService.move(eq(lotId), any(LotDtos.LotMovementRequest.class)))
                .thenReturn(new LotDtos.LotMovementResponse(UUID.randomUUID(), lotId, "MORTALITY", 3,
                        Instant.parse("2026-03-01T10:00:00Z"), "Mortality", null, Instant.now()));

        HealthDtos.MortalityResponse response = service.recordMortality(
                new HealthDtos.MortalityCreateRequest(null, lotId, 3, "Chaleur",
                        Instant.parse("2026-03-01T10:00:00Z")));

        assertThat(response.lotId()).isEqualTo(lotId);
        assertThat(response.count()).isEqualTo(3);
        verify(lotService).move(eq(lotId), any(LotDtos.LotMovementRequest.class));
    }

    @Test
    void rejectsAnimalMortalityWithCountAboveOne() {
        assertThatThrownBy(() -> service.recordMortality(new HealthDtos.MortalityCreateRequest(
                animalId, null, 2, null, Instant.parse("2026-03-01T10:00:00Z"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("one animal");
        verify(mortalityRepository, never()).save(any(MortalityEntity.class));
    }

    // P7-08

    @Test
    void recordsHealthEvent() {
        when(herdLookup.requireAnimal(tenantId, animalId)).thenReturn(animal());

        HealthDtos.HealthEventResponse response = service.recordHealthEvent(
                new HealthDtos.HealthEventCreateRequest(animalId, null, "Toux", 1, "Bronchite", "MEDIUM",
                        Instant.parse("2026-03-01T10:00:00Z")));

        assertThat(response.severity()).isEqualTo("MEDIUM");
        verify(healthEventRepository).save(any(HealthEventEntity.class));
        verify(auditService).record("HEALTH_EVENT_RECORDED", "health_event", response.id());
    }

    // P7-09

    @Test
    void createsQuarantineAndIsolatesAnimal() {
        when(herdLookup.requireAnimal(tenantId, animalId)).thenReturn(animal());

        HealthDtos.QuarantineResponse response = service.createQuarantine(
                new HealthDtos.QuarantineCreateRequest(animalId, null, LocalDate.of(2026, 3, 1), null,
                        "Suspicion"));

        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(animalService).changeStatus(eq(animalId), any(HerdDtos.AnimalStatusRequest.class));
        verify(quarantineRepository).save(any(QuarantineEntity.class));
    }

    @Test
    void releasesQuarantineAndRestoresAnimal() {
        QuarantineEntity quarantine = new QuarantineEntity();
        quarantine.id = UUID.randomUUID();
        quarantine.tenantId = tenantId;
        quarantine.animalId = animalId;
        quarantine.startDate = LocalDate.of(2026, 3, 1);
        quarantine.status = "ACTIVE";
        when(quarantineRepository.findByIdAndTenantId(quarantine.id, tenantId))
                .thenReturn(Optional.of(quarantine));

        HealthDtos.QuarantineResponse response = service.releaseQuarantine(quarantine.id);

        assertThat(response.status()).isEqualTo("RELEASED");
        verify(animalService).changeStatus(eq(animalId), any(HerdDtos.AnimalStatusRequest.class));
        verify(auditService).record("QUARANTINE_RELEASED", "quarantine", quarantine.id);
    }

    @Test
    void rejectsReleasingQuarantineTwice() {
        QuarantineEntity quarantine = new QuarantineEntity();
        quarantine.id = UUID.randomUUID();
        quarantine.tenantId = tenantId;
        quarantine.animalId = animalId;
        quarantine.status = "RELEASED";
        when(quarantineRepository.findByIdAndTenantId(quarantine.id, tenantId))
                .thenReturn(Optional.of(quarantine));

        assertThatThrownBy(() -> service.releaseQuarantine(quarantine.id))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already released");
    }

    // P7-10

    @Test
    void returnsHealthBook() {
        when(herdLookup.requireAnimal(tenantId, animalId)).thenReturn(animal());
        when(vaccinationRepository.findByTenantIdAndAnimalIdOrderByPerformedAtDesc(tenantId, animalId))
                .thenReturn(List.of());
        when(treatmentRepository.findByTenantIdAndAnimalIdOrderByStartDateDesc(tenantId, animalId))
                .thenReturn(List.of());
        when(withdrawalRepository.findByTenantIdAndAnimalIdOrderByEndDateDesc(tenantId, animalId))
                .thenReturn(List.of());
        when(mortalityRepository.findByTenantIdAndAnimalIdOrderByOccurredAtDesc(tenantId, animalId))
                .thenReturn(List.of());
        when(quarantineRepository.findByTenantIdAndAnimalIdOrderByStartDateDesc(tenantId, animalId))
                .thenReturn(List.of());

        HealthDtos.HealthBookResponse response = service.healthBook(animalId);

        assertThat(response.animalId()).isEqualTo(animalId);
        assertThat(response.vaccinations()).isEmpty();
        assertThat(response.treatments()).isEmpty();
    }

    // helpers

    private HealthDtos.ProductCreateRequest productRequest(int meatDays, int eggsDays, int milkDays) {
        return new HealthDtos.ProductCreateRequest("Vax", "VACCINE", List.of("cattle"), "2ml", "IM",
                meatDays, eggsDays, milkDays, 365, "VetCo", new BigDecimal("100"));
    }

    private HealthProductEntity product(int meatDays, int eggsDays, int milkDays) {
        HealthProductEntity product = new HealthProductEntity();
        product.id = UUID.randomUUID();
        product.tenantId = tenantId;
        product.name = "Vax";
        product.type = "VACCINE";
        product.dose = "2ml";
        product.route = "IM";
        product.withdrawalMeatDays = meatDays;
        product.withdrawalEggsDays = eggsDays;
        product.withdrawalMilkDays = milkDays;
        product.stockQty = new BigDecimal("100");
        return product;
    }

    private TreatmentEntity treatment(String status) {
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.id = UUID.randomUUID();
        treatment.tenantId = tenantId;
        treatment.animalId = animalId;
        treatment.productId = UUID.randomUUID();
        treatment.startDate = LocalDate.of(2026, 3, 1);
        treatment.status = status;
        return treatment;
    }

    private WithdrawalEntity withdrawal(UUID treatmentId, String type, LocalDate endDate) {
        WithdrawalEntity withdrawal = new WithdrawalEntity();
        withdrawal.id = UUID.randomUUID();
        withdrawal.tenantId = tenantId;
        withdrawal.treatmentId = treatmentId;
        withdrawal.animalId = animalId;
        withdrawal.type = type;
        withdrawal.startDate = LocalDate.of(2026, 3, 1);
        withdrawal.endDate = endDate;
        withdrawal.status = "ACTIVE";
        return withdrawal;
    }

    private HerdLookup.AnimalInfo animal() {
        return new HerdLookup.AnimalInfo(animalId, farmId, speciesId, "FEMALE", "ACTIVE");
    }
}
