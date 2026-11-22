package com.rmbreeding.api.production;

import com.rmbreeding.api.herd.AnimalService;
import com.rmbreeding.api.herd.HerdDtos;
import com.rmbreeding.api.herd.LotDtos;
import com.rmbreeding.api.herd.LotService;
import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.referential.ReferentialDtos;
import com.rmbreeding.api.referential.SpeciesService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionServiceTest {

    @Mock
    private WeighingRepository weighingRepository;
    @Mock
    private EggCollectionRepository collectionRepository;
    @Mock
    private EggStockRepository eggStockRepository;
    @Mock
    private EggMovementRepository eggMovementRepository;
    @Mock
    private AnimalService animalService;
    @Mock
    private LotService lotService;
    @Mock
    private SpeciesService speciesService;
    @Mock
    private AuditService auditService;

    private ProductionService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID farmId = UUID.randomUUID();
    private final UUID speciesId = UUID.randomUUID();
    private final UUID animalId = UUID.randomUUID();
    private final UUID lotId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProductionService(weighingRepository, collectionRepository, eggStockRepository,
                eggMovementRepository, animalService, lotService, speciesService, auditService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // P9-01

    @Test
    void createsAnimalWeighingAndUpdatesAnimalWeight() {
        when(animalService.get(animalId)).thenReturn(animal());

        ProductionDtos.WeighingResponse response = service.createWeighing(
                new ProductionDtos.WeighingCreateRequest(animalId, null,
                        Instant.parse("2026-03-01T08:00:00Z"), new BigDecimal("42.5"), null, null));

        assertThat(response.animalId()).isEqualTo(animalId);
        assertThat(response.weightKg()).isEqualByComparingTo("42.5");
        verify(weighingRepository).save(any(WeighingEntity.class));
        verify(animalService).patch(eq(animalId), any(HerdDtos.AnimalPatchRequest.class));
        verify(auditService).record("WEIGHING_RECORDED", "weighing", response.id());
    }

    @Test
    void createsLotWeighingWithSample() {
        when(lotService.get(lotId)).thenReturn(lot());

        ProductionDtos.WeighingResponse response = service.createWeighing(
                new ProductionDtos.WeighingCreateRequest(null, lotId,
                        Instant.parse("2026-03-01T08:00:00Z"), new BigDecimal("40"), 10, new BigDecimal("41")));

        assertThat(response.lotId()).isEqualTo(lotId);
        assertThat(response.sampleCount()).isEqualTo(10);
        verify(weighingRepository).save(any(WeighingEntity.class));
    }

    @Test
    void rejectsWeighingWithoutTarget() {
        assertThatThrownBy(() -> service.createWeighing(new ProductionDtos.WeighingCreateRequest(null, null,
                Instant.parse("2026-03-01T08:00:00Z"), new BigDecimal("40"), null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsWeighingListWithoutTarget() {
        assertThatThrownBy(() -> service.listWeighings(null, null)).isInstanceOf(BusinessRuleException.class);
    }

    // P9-02

    @Test
    void computesAverageDailyGain() {
        when(weighingRepository.findByTenantIdAndAnimalIdOrderByWeighedAtAsc(tenantId, animalId))
                .thenReturn(List.of(weighing(animalId, null, "2026-03-01T08:00:00Z", "30"),
                        weighing(animalId, null, "2026-03-11T08:00:00Z", "40")));

        ProductionDtos.GmqResponse response = service.gmq(animalId, null);

        assertThat(response.days()).isEqualTo(10);
        assertThat(response.gmq()).isEqualByComparingTo("1.000");
    }

    @Test
    void requiresTwoWeighingsForGmq() {
        when(weighingRepository.findByTenantIdAndAnimalIdOrderByWeighedAtAsc(tenantId, animalId))
                .thenReturn(List.of(weighing(animalId, null, "2026-03-01T08:00:00Z", "30")));

        assertThatThrownBy(() -> service.gmq(animalId, null)).isInstanceOf(NotFoundException.class);
    }

    // P9-03

    @Test
    void buildsGrowthCurveWithReference() {
        when(animalService.get(animalId)).thenReturn(animal());
        when(weighingRepository.findByTenantIdAndAnimalIdOrderByWeighedAtAsc(tenantId, animalId))
                .thenReturn(List.of(weighing(animalId, null, "2026-02-01T08:00:00Z", "30")));
        when(speciesService.growthCurves(speciesId)).thenReturn(List.of(
                new ReferentialDtos.GrowthCurveResponse(UUID.randomUUID(), speciesId, null, "Standard",
                        List.of(java.util.Map.of("ageDays", 30, "weightKg", 32)))));

        ProductionDtos.GrowthCurveResponse response = service.growthCurve(animalId, null);

        assertThat(response.speciesId()).isEqualTo(speciesId);
        assertThat(response.points()).hasSize(1);
        assertThat(response.points().get(0).ageDays()).isEqualTo(17);
        assertThat(response.reference()).hasSize(1);
    }

    // P9-07

    @Test
    void recordsCollectionAndUpdatesStock() {
        when(lotService.get(lotId)).thenReturn(lot());
        when(eggStockRepository.findByTenantIdAndFarmIdAndLotId(tenantId, farmId, lotId))
                .thenReturn(Optional.empty());

        ProductionDtos.EggCollectionResponse response = service.createCollection(
                new ProductionDtos.EggCollectionCreateRequest(lotId, Instant.parse("2026-03-01T06:00:00Z"),
                        100, 5, 3));

        assertThat(response.sellable()).isEqualTo(92);
        verify(collectionRepository).save(any(EggCollectionEntity.class));
        verify(eggStockRepository).save(any(EggStockEntity.class));
        verify(eggMovementRepository).save(any(EggMovementEntity.class));
        verify(auditService).record("EGG_COLLECTION_RECORDED", "egg_collection", response.id());
    }

    @Test
    void rejectsCollectionWhenBrokenExceedCollected() {
        when(lotService.get(lotId)).thenReturn(lot());

        assertThatThrownBy(() -> service.createCollection(new ProductionDtos.EggCollectionCreateRequest(
                lotId, Instant.parse("2026-03-01T06:00:00Z"), 10, 8, 5)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsCollectionWithoutLot() {
        assertThatThrownBy(() -> service.createCollection(new ProductionDtos.EggCollectionCreateRequest(
                null, Instant.parse("2026-03-01T06:00:00Z"), 10, 0, 0)))
                .isInstanceOf(BusinessRuleException.class);
    }

    // P9-08

    @Test
    void saleMovementDecrementsStock() {
        when(lotService.get(lotId)).thenReturn(lot());
        EggStockEntity stock = eggStock(50);
        when(eggStockRepository.findByTenantIdAndFarmIdAndLotId(tenantId, farmId, lotId))
                .thenReturn(Optional.of(stock));

        service.createMovement(new ProductionDtos.EggMovementCreateRequest(lotId, "SALE", 20,
                Instant.parse("2026-03-02T10:00:00Z"), null));

        assertThat(stock.quantity).isEqualTo(30);
        verify(auditService).record(eq("EGG_MOVEMENT_RECORDED"), eq("egg_movement"), any());
    }

    @Test
    void rejectsMovementWhenStockInsufficient() {
        when(lotService.get(lotId)).thenReturn(lot());
        when(eggStockRepository.findByTenantIdAndFarmIdAndLotId(tenantId, farmId, lotId))
                .thenReturn(Optional.of(eggStock(5)));

        assertThatThrownBy(() -> service.createMovement(new ProductionDtos.EggMovementCreateRequest(
                lotId, "SALE", 20, Instant.parse("2026-03-02T10:00:00Z"), null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void adjustmentMovementIncrementsStock() {
        when(lotService.get(lotId)).thenReturn(lot());
        EggStockEntity stock = eggStock(5);
        when(eggStockRepository.findByTenantIdAndFarmIdAndLotId(tenantId, farmId, lotId))
                .thenReturn(Optional.of(stock));

        service.createMovement(new ProductionDtos.EggMovementCreateRequest(lotId, "ADJUSTMENT", 10,
                Instant.parse("2026-03-02T10:00:00Z"), null));

        assertThat(stock.quantity).isEqualTo(15);
    }

    // P9-09

    @Test
    void computesLayingRate() {
        when(lotService.get(lotId)).thenReturn(lot());
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-10T00:00:00Z");
        EggCollectionEntity first = collection("2026-03-01T06:00:00Z", 90, 0, 0);
        EggCollectionEntity second = collection("2026-03-02T06:00:00Z", 110, 0, 0);
        when(collectionRepository.findByTenantIdAndLotIdAndCollectedAtBetween(tenantId, lotId, from, to))
                .thenReturn(List.of(first, second));

        ProductionDtos.LayingRateResponse response = service.layingRate(lotId, from, to);

        assertThat(response.days()).isEqualTo(10);
        assertThat(response.henCount()).isEqualTo(100);
        assertThat(response.totalCollected()).isEqualTo(200);
        assertThat(response.layingRate()).isEqualByComparingTo("20.00");
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

    private WeighingEntity weighing(UUID animalId, UUID lotId, String weighedAt, String weightKg) {
        WeighingEntity weighing = new WeighingEntity();
        weighing.id = UUID.randomUUID();
        weighing.tenantId = tenantId;
        weighing.animalId = animalId;
        weighing.lotId = lotId;
        weighing.weighedAt = Instant.parse(weighedAt);
        weighing.weightKg = new BigDecimal(weightKg);
        return weighing;
    }

    private EggStockEntity eggStock(int quantity) {
        EggStockEntity stock = new EggStockEntity();
        stock.id = UUID.randomUUID();
        stock.tenantId = tenantId;
        stock.farmId = farmId;
        stock.lotId = lotId;
        stock.quantity = quantity;
        return stock;
    }

    private EggCollectionEntity collection(String collectedAt, int collected, int broken, int downgraded) {
        EggCollectionEntity collection = new EggCollectionEntity();
        collection.id = UUID.randomUUID();
        collection.tenantId = tenantId;
        collection.farmId = farmId;
        collection.lotId = lotId;
        collection.collectedAt = Instant.parse(collectedAt);
        collection.collected = collected;
        collection.broken = broken;
        collection.downgraded = downgraded;
        collection.sellable = collected - broken - downgraded;
        return collection;
    }
}
