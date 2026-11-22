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
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductionService {

    private final WeighingRepository weighingRepository;
    private final EggCollectionRepository collectionRepository;
    private final EggStockRepository eggStockRepository;
    private final EggMovementRepository eggMovementRepository;
    private final AnimalService animalService;
    private final LotService lotService;
    private final SpeciesService speciesService;
    private final AuditService auditService;

    public ProductionService(WeighingRepository weighingRepository,
            EggCollectionRepository collectionRepository,
            EggStockRepository eggStockRepository,
            EggMovementRepository eggMovementRepository,
            AnimalService animalService,
            LotService lotService,
            SpeciesService speciesService,
            AuditService auditService) {
        this.weighingRepository = weighingRepository;
        this.collectionRepository = collectionRepository;
        this.eggStockRepository = eggStockRepository;
        this.eggMovementRepository = eggMovementRepository;
        this.animalService = animalService;
        this.lotService = lotService;
        this.speciesService = speciesService;
        this.auditService = auditService;
    }

    // P9-01 Weighings

    public List<ProductionDtos.WeighingResponse> listWeighings(UUID animalId, UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        List<WeighingEntity> weighings;
        if (animalId != null) {
            weighings = weighingRepository.findByTenantIdAndAnimalIdOrderByWeighedAtAsc(tenantId, animalId);
        } else if (lotId != null) {
            weighings = weighingRepository.findByTenantIdAndLotIdOrderByWeighedAtAsc(tenantId, lotId);
        } else {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "animalId or lotId is required");
        }
        return weighings.stream().map(this::toWeighingResponse).toList();
    }

    @Transactional
    public ProductionDtos.WeighingResponse createWeighing(ProductionDtos.WeighingCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        if (request.animalId() == null && request.lotId() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A weighing target (animal or lot) is required");
        }
        if (request.animalId() != null) {
            animalService.get(request.animalId());
        }
        if (request.lotId() != null) {
            lotService.get(request.lotId());
        }
        WeighingEntity weighing = new WeighingEntity();
        weighing.id = UUID.randomUUID();
        weighing.tenantId = tenantId;
        weighing.animalId = request.animalId();
        weighing.lotId = request.lotId();
        weighing.weighedAt = request.weighedAt();
        weighing.weightKg = request.weightKg();
        weighing.sampleCount = request.sampleCount();
        weighing.averageWeight = request.averageWeight();
        weighingRepository.save(weighing);
        if (request.animalId() != null) {
            animalService.patch(request.animalId(), new HerdDtos.AnimalPatchRequest(null, null, null, null, null,
                    null, null, null, null, null, request.weightKg(), null));
        }
        auditService.record("WEIGHING_RECORDED", "weighing", weighing.id);
        return toWeighingResponse(weighing);
    }

    // P9-02 Average daily gain

    public ProductionDtos.GmqResponse gmq(UUID animalId, UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        if (animalId == null && lotId == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "animalId or lotId is required");
        }
        List<WeighingEntity> weighings = animalId != null
                ? weighingRepository.findByTenantIdAndAnimalIdOrderByWeighedAtAsc(tenantId, animalId)
                : weighingRepository.findByTenantIdAndLotIdOrderByWeighedAtAsc(tenantId, lotId);
        if (weighings.size() < 2) {
            throw new NotFoundException("At least two weighings are required to compute GMQ");
        }
        WeighingEntity first = weighings.get(0);
        WeighingEntity last = weighings.get(weighings.size() - 1);
        BigDecimal startWeight = weightOf(first);
        BigDecimal endWeight = weightOf(last);
        long days = Math.max(1, ChronoUnit.DAYS.between(first.weighedAt, last.weighedAt));
        BigDecimal gmq = endWeight.subtract(startWeight)
                .divide(BigDecimal.valueOf(days), 3, RoundingMode.HALF_UP);
        return new ProductionDtos.GmqResponse(animalId, lotId, first.weighedAt, last.weighedAt, days,
                startWeight, endWeight, gmq);
    }

    // P9-03 Growth curves

    public ProductionDtos.GrowthCurveResponse growthCurve(UUID animalId, UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        if (animalId == null && lotId == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "animalId or lotId is required");
        }
        UUID speciesId;
        LocalDate referenceDate;
        List<WeighingEntity> weighings;
        if (animalId != null) {
            HerdDtos.AnimalResponse animal = animalService.get(animalId);
            speciesId = animal.speciesId();
            referenceDate = animal.birthDate();
            weighings = weighingRepository.findByTenantIdAndAnimalIdOrderByWeighedAtAsc(tenantId, animalId);
        } else {
            LotDtos.LotResponse lot = lotService.get(lotId);
            speciesId = lot.speciesId();
            referenceDate = lot.entryDate();
            weighings = weighingRepository.findByTenantIdAndLotIdOrderByWeighedAtAsc(tenantId, lotId);
        }
        List<ProductionDtos.GrowthPoint> points = new ArrayList<>();
        for (WeighingEntity weighing : weighings) {
            points.add(new ProductionDtos.GrowthPoint(weighing.weighedAt, weightOf(weighing),
                    ageDays(referenceDate, weighing.weighedAt)));
        }
        List<Map<String, Object>> reference = new ArrayList<>();
        if (speciesId != null) {
            List<ReferentialDtos.GrowthCurveResponse> curves = speciesService.growthCurves(speciesId);
            if (!curves.isEmpty()) {
                reference = curves.get(0).points();
            }
        }
        return new ProductionDtos.GrowthCurveResponse(animalId, lotId, speciesId, points, reference);
    }

    // P9-07 Egg collections

    @Transactional
    public ProductionDtos.EggCollectionResponse createCollection(ProductionDtos.EggCollectionCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        if (request.lotId() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A lot is required for an egg collection");
        }
        LotDtos.LotResponse lot = lotService.get(request.lotId());
        int broken = request.broken() == null ? 0 : request.broken();
        int downgraded = request.downgraded() == null ? 0 : request.downgraded();
        int sellable = request.collected() - broken - downgraded;
        if (sellable < 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Broken and downgraded eggs cannot exceed collected eggs");
        }
        EggCollectionEntity collection = new EggCollectionEntity();
        collection.id = UUID.randomUUID();
        collection.tenantId = tenantId;
        collection.farmId = lot.farmId();
        collection.lotId = lot.id();
        collection.collectedAt = request.collectedAt();
        collection.collected = request.collected();
        collection.broken = broken;
        collection.downgraded = downgraded;
        collection.sellable = sellable;
        collectionRepository.save(collection);
        applyEggStockDelta(tenantId, lot.farmId(), lot.id(), sellable);
        recordEggMovement(tenantId, lot.farmId(), lot.id(), "COLLECTION", sellable, request.collectedAt(),
                "Ramassage");
        auditService.record("EGG_COLLECTION_RECORDED", "egg_collection", collection.id);
        return toCollectionResponse(collection);
    }

    public List<ProductionDtos.EggCollectionResponse> listCollections(UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        List<EggCollectionEntity> collections = lotId == null
                ? collectionRepository.findByTenantIdOrderByCollectedAtDesc(tenantId)
                : collectionRepository.findByTenantIdAndLotIdOrderByCollectedAtDesc(tenantId, lotId);
        return collections.stream().map(this::toCollectionResponse).toList();
    }

    // P9-08 Egg movements and stock

    @Transactional
    public ProductionDtos.EggMovementResponse createMovement(ProductionDtos.EggMovementCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        if (request.lotId() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A lot is required for an egg movement");
        }
        LotDtos.LotResponse lot = lotService.get(request.lotId());
        int delta = "SALE".equals(request.type()) || "LOSS".equals(request.type())
                ? -request.quantity()
                : request.quantity();
        applyEggStockDelta(tenantId, lot.farmId(), lot.id(), delta);
        EggMovementEntity movement = recordEggMovement(tenantId, lot.farmId(), lot.id(), request.type(),
                request.quantity(), request.occurredAt(), request.reason());
        auditService.record("EGG_MOVEMENT_RECORDED", "egg_movement", movement.id);
        return toMovementResponse(movement);
    }

    public List<ProductionDtos.EggMovementResponse> listMovements(UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        List<EggMovementEntity> movements = lotId == null
                ? eggMovementRepository.findByTenantIdOrderByOccurredAtDesc(tenantId)
                : eggMovementRepository.findByTenantIdAndLotIdOrderByOccurredAtDesc(tenantId, lotId);
        return movements.stream().map(this::toMovementResponse).toList();
    }

    public List<ProductionDtos.EggStockResponse> listEggStocks() {
        UUID tenantId = TenantContext.requireTenantId();
        return eggStockRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                .map(this::toEggStockResponse).toList();
    }

    // P9-09 Laying rate

    public ProductionDtos.LayingRateResponse layingRate(UUID lotId, Instant from, Instant to) {
        UUID tenantId = TenantContext.requireTenantId();
        if (lotId == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "lotId is required");
        }
        if (from == null || to == null || from.isAfter(to)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A valid period is required");
        }
        LotDtos.LotResponse lot = lotService.get(lotId);
        List<EggCollectionEntity> collections = collectionRepository
                .findByTenantIdAndLotIdAndCollectedAtBetween(tenantId, lotId, from, to);
        int totalCollected = collections.stream().mapToInt(collection -> collection.collected).sum();
        long days = Math.max(1, ChronoUnit.DAYS.between(from, to) + 1);
        int henCount = lot.currentCount();
        BigDecimal layingRate = BigDecimal.ZERO;
        if (henCount > 0) {
            layingRate = BigDecimal.valueOf(totalCollected)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf((long) henCount * days), 2, RoundingMode.HALF_UP);
        }
        return new ProductionDtos.LayingRateResponse(lotId, from, to, days, henCount, totalCollected, layingRate);
    }

    // Helpers

    private void applyEggStockDelta(UUID tenantId, UUID farmId, UUID lotId, int delta) {
        EggStockEntity stock = (lotId == null
                ? eggStockRepository.findByTenantIdAndFarmIdAndLotIdIsNull(tenantId, farmId)
                : eggStockRepository.findByTenantIdAndFarmIdAndLotId(tenantId, farmId, lotId))
                .orElseGet(() -> {
                    EggStockEntity created = new EggStockEntity();
                    created.id = UUID.randomUUID();
                    created.tenantId = tenantId;
                    created.farmId = farmId;
                    created.lotId = lotId;
                    created.quantity = 0;
                    return created;
                });
        int newQuantity = stock.quantity + delta;
        if (newQuantity < 0) {
            throw new ConflictException("Insufficient egg stock");
        }
        stock.quantity = newQuantity;
        stock.updatedAt = Instant.now();
        eggStockRepository.save(stock);
    }

    private EggMovementEntity recordEggMovement(UUID tenantId, UUID farmId, UUID lotId, String type,
            int quantity, Instant occurredAt, String reason) {
        EggMovementEntity movement = new EggMovementEntity();
        movement.id = UUID.randomUUID();
        movement.tenantId = tenantId;
        movement.farmId = farmId;
        movement.lotId = lotId;
        movement.type = type;
        movement.quantity = quantity;
        movement.occurredAt = occurredAt;
        movement.reason = reason;
        eggMovementRepository.save(movement);
        return movement;
    }

    private BigDecimal weightOf(WeighingEntity weighing) {
        if (weighing.lotId != null && weighing.averageWeight != null) {
            return weighing.averageWeight;
        }
        return weighing.weightKg;
    }

    private Integer ageDays(LocalDate referenceDate, Instant weighedAt) {
        if (referenceDate == null || weighedAt == null) {
            return null;
        }
        LocalDate date = weighedAt.atZone(ZoneOffset.UTC).toLocalDate();
        return (int) Math.max(0, ChronoUnit.DAYS.between(referenceDate, date));
    }

    private ProductionDtos.WeighingResponse toWeighingResponse(WeighingEntity weighing) {
        return new ProductionDtos.WeighingResponse(weighing.id, weighing.animalId, weighing.lotId,
                weighing.weighedAt, weighing.weightKg, weighing.sampleCount, weighing.averageWeight);
    }

    private ProductionDtos.EggCollectionResponse toCollectionResponse(EggCollectionEntity collection) {
        return new ProductionDtos.EggCollectionResponse(collection.id, collection.farmId, collection.lotId,
                collection.collectedAt, collection.collected, collection.broken, collection.downgraded,
                collection.sellable);
    }

    private ProductionDtos.EggMovementResponse toMovementResponse(EggMovementEntity movement) {
        return new ProductionDtos.EggMovementResponse(movement.id, movement.farmId, movement.lotId,
                movement.type, movement.quantity, movement.occurredAt, movement.reason);
    }

    private ProductionDtos.EggStockResponse toEggStockResponse(EggStockEntity stock) {
        return new ProductionDtos.EggStockResponse(stock.farmId, stock.lotId, stock.quantity, stock.updatedAt);
    }
}
