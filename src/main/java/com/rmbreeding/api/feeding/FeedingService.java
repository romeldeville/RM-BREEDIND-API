package com.rmbreeding.api.feeding;

import com.rmbreeding.api.herd.HerdLookup;
import com.rmbreeding.api.herd.LotService;
import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.identity.AuthContext;
import com.rmbreeding.api.referential.ReferentialDtos;
import com.rmbreeding.api.referential.SpeciesService;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FeedingService {

    private final FeedRepository feedRepository;
    private final FeedReceiptRepository receiptRepository;
    private final FeedDistributionRepository distributionRepository;
    private final FeedStockRepository stockRepository;
    private final FeedCostRepository costRepository;
    private final HerdLookup herdLookup;
    private final LotService lotService;
    private final SpeciesService speciesService;
    private final AuditService auditService;

    public FeedingService(FeedRepository feedRepository,
            FeedReceiptRepository receiptRepository,
            FeedDistributionRepository distributionRepository,
            FeedStockRepository stockRepository,
            FeedCostRepository costRepository,
            HerdLookup herdLookup,
            LotService lotService,
            SpeciesService speciesService,
            AuditService auditService) {
        this.feedRepository = feedRepository;
        this.receiptRepository = receiptRepository;
        this.distributionRepository = distributionRepository;
        this.stockRepository = stockRepository;
        this.costRepository = costRepository;
        this.herdLookup = herdLookup;
        this.lotService = lotService;
        this.speciesService = speciesService;
        this.auditService = auditService;
    }

    // P8-01 Feeds

    public List<FeedingDtos.FeedResponse> listFeeds(String search, String type, boolean includeArchived) {
        UUID tenantId = TenantContext.requireTenantId();
        Specification<FeedEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("tenantId"), tenantId));
            if (!includeArchived) {
                predicates.add(builder.isNull(root.get("archivedAt")));
            }
            if (type != null && !type.isBlank()) {
                predicates.add(builder.equal(root.get("type"), type));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(builder.like(builder.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return feedRepository.findAll(specification, Sort.by("name")).stream()
                .map(this::toFeedResponse).toList();
    }

    @Transactional
    public FeedingDtos.FeedResponse createFeed(FeedingDtos.FeedCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        FeedEntity feed = new FeedEntity();
        feed.id = UUID.randomUUID();
        feed.tenantId = tenantId;
        feed.name = request.name();
        feed.type = request.type();
        feed.unit = request.unit();
        feed.price = request.price();
        feed.supplier = request.supplier();
        feedRepository.save(feed);
        auditService.record("FEED_CREATED", "feed", feed.id);
        return toFeedResponse(feed);
    }

    public FeedingDtos.FeedResponse getFeed(UUID feedId) {
        return toFeedResponse(requireFeed(feedId, TenantContext.requireTenantId()));
    }

    @Transactional
    public FeedingDtos.FeedResponse patchFeed(UUID feedId, FeedingDtos.FeedPatchRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        FeedEntity feed = requireFeed(feedId, tenantId);
        if (request.name() != null) {
            feed.name = request.name();
        }
        if (request.type() != null) {
            feed.type = request.type();
        }
        if (request.unit() != null) {
            feed.unit = request.unit();
        }
        if (request.price() != null) {
            feed.price = request.price();
        }
        if (request.supplier() != null) {
            feed.supplier = request.supplier();
        }
        feedRepository.save(feed);
        auditService.record("FEED_UPDATED", "feed", feed.id);
        return toFeedResponse(feed);
    }

    @Transactional
    public void archiveFeed(UUID feedId) {
        UUID tenantId = TenantContext.requireTenantId();
        FeedEntity feed = requireFeed(feedId, tenantId);
        if (feed.archivedAt != null) {
            throw new ConflictException("Feed is already archived");
        }
        feed.archivedAt = Instant.now();
        feedRepository.save(feed);
        auditService.record("FEED_ARCHIVED", "feed", feed.id);
    }

    // P8-02 Receipts

    @Transactional
    public FeedingDtos.FeedReceiptResponse receiveFeed(UUID feedId, FeedingDtos.FeedReceiptCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        FeedEntity feed = requireFeed(feedId, tenantId);
        FeedReceiptEntity receipt = new FeedReceiptEntity();
        receipt.id = UUID.randomUUID();
        receipt.tenantId = tenantId;
        receipt.feedId = feed.id;
        receipt.quantity = request.quantity();
        receipt.unitPrice = request.unitPrice();
        receipt.receivedAt = request.receivedAt();
        receipt.supplier = request.supplier() != null ? request.supplier() : feed.supplier;
        receipt.expiryDate = request.expiryDate();
        receipt.notes = request.notes();
        receiptRepository.save(receipt);
        applyStockDelta(tenantId, feed, request.quantity(), request.unitPrice());
        auditService.record("FEED_RECEIPT_RECORDED", "feed_receipt", receipt.id);
        return toReceiptResponse(receipt);
    }

    public List<FeedingDtos.FeedReceiptResponse> listReceipts(UUID feedId) {
        UUID tenantId = TenantContext.requireTenantId();
        requireFeed(feedId, tenantId);
        return receiptRepository.findByTenantIdAndFeedIdOrderByReceivedAtDesc(tenantId, feedId).stream()
                .map(this::toReceiptResponse).toList();
    }

    public List<FeedingDtos.FeedReceiptResponse> expiringReceipts(LocalDate before) {
        UUID tenantId = TenantContext.requireTenantId();
        return receiptRepository.findByTenantIdAndExpiryDateLessThanEqualOrderByExpiryDateAsc(tenantId, before)
                .stream().map(this::toReceiptResponse).toList();
    }

    // P8-03 Distributions

    @Transactional
    public FeedingDtos.FeedDistributionResponse distribute(FeedingDtos.FeedDistributionCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        FeedEntity feed = requireFeed(request.feedId(), tenantId);
        if (request.lotId() == null && request.animalId() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A distribution target (lot or animal) is required");
        }
        if (request.lotId() != null) {
            lotService.get(request.lotId());
        }
        if (request.animalId() != null) {
            herdLookup.requireAnimal(tenantId, request.animalId());
        }
        if (request.planId() != null) {
            speciesService.requireFeedingPlan(request.planId());
        }
        FeedStockEntity stock = stockRepository.findByTenantIdAndFeedId(tenantId, feed.id)
                .orElseThrow(() -> new ConflictException("Insufficient stock"));
        if (stock.quantity.compareTo(request.quantity()) < 0) {
            throw new ConflictException("Insufficient stock");
        }
        applyStockDelta(tenantId, feed, request.quantity().negate(), null);

        FeedDistributionEntity distribution = new FeedDistributionEntity();
        distribution.id = UUID.randomUUID();
        distribution.tenantId = tenantId;
        distribution.feedId = feed.id;
        distribution.lotId = request.lotId();
        distribution.animalId = request.animalId();
        distribution.quantity = request.quantity();
        distribution.distributedAt = request.distributedAt();
        distribution.actorId = AuthContext.userId();
        distribution.planId = request.planId();
        distribution.notes = request.notes();
        distributionRepository.save(distribution);
        auditService.record("FEED_DISTRIBUTED", "feed_distribution", distribution.id);
        return toDistributionResponse(distribution);
    }

    public List<FeedingDtos.FeedDistributionResponse> listDistributions(UUID feedId) {
        UUID tenantId = TenantContext.requireTenantId();
        List<FeedDistributionEntity> distributions = feedId == null
                ? distributionRepository.findByTenantIdOrderByDistributedAtDesc(tenantId)
                : distributionRepository.findByTenantIdAndFeedIdOrderByDistributedAtDesc(tenantId, feedId);
        return distributions.stream().map(this::toDistributionResponse).toList();
    }

    // P8-04 Feeding plans

    public List<ReferentialDtos.FeedingPlanResponse> listFeedingPlans(UUID speciesId) {
        return speciesId == null ? speciesService.allFeedingPlans() : speciesService.feedingPlans(speciesId);
    }

    // P8-05 Stock projection

    public List<FeedingDtos.FeedStockResponse> listStocks() {
        UUID tenantId = TenantContext.requireTenantId();
        return stockRepository.findByTenantId(tenantId).stream()
                .map(stock -> toStockResponse(tenantId, stock)).toList();
    }

    // P8-06 Feed costs

    @Transactional
    public FeedingDtos.FeedCostResponse computeCost(FeedingDtos.FeedCostComputeRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        if (request.lotId() == null && request.animalId() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A cost target (lot or animal) is required");
        }
        if (request.from().isAfter(request.to())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "from must be before to");
        }
        List<FeedDistributionEntity> distributions = request.lotId() != null
                ? distributionRepository.findByTenantIdAndLotIdAndDistributedAtBetweenOrderByDistributedAtDesc(
                        tenantId, request.lotId(), request.from(), request.to())
                : distributionRepository.findByTenantIdAndAnimalIdAndDistributedAtBetweenOrderByDistributedAtDesc(
                        tenantId, request.animalId(), request.from(), request.to());
        BigDecimal amount = BigDecimal.ZERO;
        for (FeedDistributionEntity distribution : distributions) {
            BigDecimal unitPrice = unitPriceFor(tenantId, distribution.feedId);
            amount = amount.add(distribution.quantity.multiply(unitPrice));
        }
        FeedCostEntity cost = new FeedCostEntity();
        cost.id = UUID.randomUUID();
        cost.tenantId = tenantId;
        cost.lotId = request.lotId();
        cost.animalId = request.animalId();
        cost.amount = amount.setScale(2, RoundingMode.HALF_UP);
        cost.periodStart = request.from().atZone(ZoneOffset.UTC).toLocalDate();
        cost.periodEnd = request.to().atZone(ZoneOffset.UTC).toLocalDate();
        costRepository.save(cost);
        auditService.record("FEED_COST_COMPUTED", "feed_cost", cost.id);
        return toCostResponse(cost);
    }

    public List<FeedingDtos.FeedCostResponse> listCosts() {
        UUID tenantId = TenantContext.requireTenantId();
        return costRepository.findByTenantIdOrderByPeriodEndDesc(tenantId).stream()
                .map(this::toCostResponse).toList();
    }

    // P8-07 Low stock rule

    public List<FeedingDtos.LowStockAlertResponse> lowStock(BigDecimal threshold) {
        UUID tenantId = TenantContext.requireTenantId();
        BigDecimal effective = threshold == null ? BigDecimal.ZERO : threshold;
        List<FeedingDtos.LowStockAlertResponse> alerts = new ArrayList<>();
        for (FeedStockEntity stock : stockRepository.findByTenantId(tenantId)) {
            if (stock.quantity.compareTo(effective) < 0) {
                FeedEntity feed = feedRepository.findByIdAndTenantId(stock.feedId, tenantId).orElse(null);
                alerts.add(new FeedingDtos.LowStockAlertResponse(stock.feedId,
                        feed != null ? feed.name : null,
                        feed != null ? feed.unit : null,
                        stock.quantity, effective));
            }
        }
        return alerts;
    }

    // Helpers

    private void applyStockDelta(UUID tenantId, FeedEntity feed, BigDecimal delta, BigDecimal unitPrice) {
        FeedStockEntity stock = stockRepository.findByTenantIdAndFeedId(tenantId, feed.id)
                .orElseGet(() -> {
                    FeedStockEntity created = new FeedStockEntity();
                    created.id = UUID.randomUUID();
                    created.tenantId = tenantId;
                    created.feedId = feed.id;
                    created.quantity = BigDecimal.ZERO;
                    created.averagePrice = feed.price;
                    return created;
                });
        BigDecimal previousQuantity = stock.quantity;
        BigDecimal newQuantity = previousQuantity.add(delta);
        if (newQuantity.signum() < 0) {
            throw new ConflictException("Insufficient stock");
        }
        if (delta.signum() > 0 && unitPrice != null) {
            BigDecimal previousValue = previousQuantity.multiply(
                    stock.averagePrice != null ? stock.averagePrice : BigDecimal.ZERO);
            BigDecimal addedValue = delta.multiply(unitPrice);
            if (newQuantity.signum() > 0) {
                stock.averagePrice = previousValue.add(addedValue)
                        .divide(newQuantity, 4, RoundingMode.HALF_UP);
            } else {
                stock.averagePrice = unitPrice;
            }
        }
        stock.quantity = newQuantity;
        stock.updatedAt = Instant.now();
        stockRepository.save(stock);
    }

    private BigDecimal unitPriceFor(UUID tenantId, UUID feedId) {
        return stockRepository.findByTenantIdAndFeedId(tenantId, feedId)
                .map(stock -> stock.averagePrice != null ? stock.averagePrice : BigDecimal.ZERO)
                .orElseGet(() -> feedRepository.findByIdAndTenantId(feedId, tenantId)
                        .map(feed -> feed.price != null ? feed.price : BigDecimal.ZERO)
                        .orElse(BigDecimal.ZERO));
    }

    private FeedEntity requireFeed(UUID feedId, UUID tenantId) {
        return feedRepository.findByIdAndTenantId(feedId, tenantId)
                .orElseThrow(() -> new NotFoundException("Feed not found"));
    }

    private FeedingDtos.FeedResponse toFeedResponse(FeedEntity feed) {
        return new FeedingDtos.FeedResponse(feed.id, feed.name, feed.type, feed.unit, feed.price,
                feed.supplier, feed.archivedAt);
    }

    private FeedingDtos.FeedReceiptResponse toReceiptResponse(FeedReceiptEntity receipt) {
        return new FeedingDtos.FeedReceiptResponse(receipt.id, receipt.feedId, receipt.quantity,
                receipt.unitPrice, receipt.receivedAt, receipt.supplier, receipt.expiryDate, receipt.notes);
    }

    private FeedingDtos.FeedDistributionResponse toDistributionResponse(FeedDistributionEntity distribution) {
        return new FeedingDtos.FeedDistributionResponse(distribution.id, distribution.feedId,
                distribution.lotId, distribution.animalId, distribution.quantity, distribution.distributedAt,
                distribution.actorId, distribution.planId, distribution.notes);
    }

    private FeedingDtos.FeedStockResponse toStockResponse(UUID tenantId, FeedStockEntity stock) {
        FeedEntity feed = feedRepository.findByIdAndTenantId(stock.feedId, tenantId).orElse(null);
        return new FeedingDtos.FeedStockResponse(stock.feedId, feed != null ? feed.name : null,
                feed != null ? feed.unit : null, stock.quantity, stock.averagePrice, stock.updatedAt);
    }

    private FeedingDtos.FeedCostResponse toCostResponse(FeedCostEntity cost) {
        return new FeedingDtos.FeedCostResponse(cost.id, cost.lotId, cost.animalId, cost.amount,
                cost.periodStart, cost.periodEnd);
    }

    public BigDecimal feedCostForLot(UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        List<FeedDistributionEntity> distributions = distributionRepository
                .findByTenantIdAndLotIdOrderByDistributedAtDesc(tenantId, lotId);
        BigDecimal total = BigDecimal.ZERO;
        for (FeedDistributionEntity distribution : distributions) {
            BigDecimal price = feedRepository.findByIdAndTenantId(distribution.feedId, tenantId)
                    .map(feed -> feed.price)
                    .orElse(null);
            if (price != null) {
                total = total.add(distribution.quantity.multiply(price));
            }
        }
        return total;
    }
}
