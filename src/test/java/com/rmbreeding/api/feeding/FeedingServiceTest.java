package com.rmbreeding.api.feeding;

import com.rmbreeding.api.herd.HerdLookup;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedingServiceTest {

    @Mock
    private FeedRepository feedRepository;
    @Mock
    private FeedReceiptRepository receiptRepository;
    @Mock
    private FeedDistributionRepository distributionRepository;
    @Mock
    private FeedStockRepository stockRepository;
    @Mock
    private FeedCostRepository costRepository;
    @Mock
    private HerdLookup herdLookup;
    @Mock
    private LotService lotService;
    @Mock
    private SpeciesService speciesService;
    @Mock
    private AuditService auditService;

    private FeedingService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID feedId = UUID.randomUUID();
    private final UUID lotId = UUID.randomUUID();
    private final UUID animalId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FeedingService(feedRepository, receiptRepository, distributionRepository,
                stockRepository, costRepository, herdLookup, lotService, speciesService, auditService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // P8-01

    @Test
    void createsFeed() {
        FeedingDtos.FeedResponse response = service.createFeed(
                new FeedingDtos.FeedCreateRequest("Maïs", "CONCENTRATE", "kg", new BigDecimal("0.35"), "Sarl"));

        assertThat(response.name()).isEqualTo("Maïs");
        assertThat(response.unit()).isEqualTo("kg");
        verify(feedRepository).save(any(FeedEntity.class));
        verify(auditService).record("FEED_CREATED", "feed", response.id());
    }

    @Test
    void patchesFeedFields() {
        FeedEntity feed = feed();
        when(feedRepository.findByIdAndTenantId(feed.id, tenantId)).thenReturn(Optional.of(feed));

        FeedingDtos.FeedResponse response = service.patchFeed(feed.id,
                new FeedingDtos.FeedPatchRequest("Maïs concassé", null, null, new BigDecimal("0.40"), null));

        assertThat(response.name()).isEqualTo("Maïs concassé");
        assertThat(response.price()).isEqualByComparingTo("0.40");
        verify(auditService).record("FEED_UPDATED", "feed", feed.id);
    }

    @Test
    void rejectsArchivingAnAlreadyArchivedFeed() {
        FeedEntity feed = feed();
        feed.archivedAt = Instant.now();
        when(feedRepository.findByIdAndTenantId(feed.id, tenantId)).thenReturn(Optional.of(feed));

        assertThatThrownBy(() -> service.archiveFeed(feed.id)).isInstanceOf(ConflictException.class);
    }

    @Test
    void archivesFeed() {
        FeedEntity feed = feed();
        when(feedRepository.findByIdAndTenantId(feed.id, tenantId)).thenReturn(Optional.of(feed));

        service.archiveFeed(feed.id);

        assertThat(feed.archivedAt).isNotNull();
        verify(auditService).record("FEED_ARCHIVED", "feed", feed.id);
    }

    @Test
    void throwsWhenFeedNotFound() {
        when(feedRepository.findByIdAndTenantId(feedId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getFeed(feedId)).isInstanceOf(NotFoundException.class);
    }

    // P8-02

    @Test
    void receivingFeedUpdatesStockWithWeightedAverage() {
        FeedEntity feed = feed();
        when(feedRepository.findByIdAndTenantId(feed.id, tenantId)).thenReturn(Optional.of(feed));
        FeedStockEntity stock = stock(new BigDecimal("100"), new BigDecimal("0.30"));
        when(stockRepository.findByTenantIdAndFeedId(tenantId, feed.id)).thenReturn(Optional.of(stock));

        service.receiveFeed(feed.id, new FeedingDtos.FeedReceiptCreateRequest(
                new BigDecimal("100"), new BigDecimal("0.50"), Instant.parse("2026-03-01T08:00:00Z"),
                "Fournisseur", LocalDate.parse("2026-06-01"), null));

        assertThat(stock.quantity).isEqualByComparingTo("200");
        assertThat(stock.averagePrice).isEqualByComparingTo("0.40");
        verify(receiptRepository).save(any(FeedReceiptEntity.class));
        verify(auditService).record(org.mockito.ArgumentMatchers.eq("FEED_RECEIPT_RECORDED"),
                org.mockito.ArgumentMatchers.eq("feed_receipt"), any());
    }

    @Test
    void listsReceiptsForFeed() {
        FeedEntity feed = feed();
        when(feedRepository.findByIdAndTenantId(feed.id, tenantId)).thenReturn(Optional.of(feed));
        when(receiptRepository.findByTenantIdAndFeedIdOrderByReceivedAtDesc(tenantId, feed.id))
                .thenReturn(List.of());

        assertThat(service.listReceipts(feed.id)).isEmpty();
    }

    // P8-03

    @Test
    void distributesFeedAndDecrementsStock() {
        FeedEntity feed = feed();
        when(feedRepository.findByIdAndTenantId(feed.id, tenantId)).thenReturn(Optional.of(feed));
        FeedStockEntity stock = stock(new BigDecimal("50"), new BigDecimal("0.35"));
        when(stockRepository.findByTenantIdAndFeedId(tenantId, feed.id)).thenReturn(Optional.of(stock));

        FeedingDtos.FeedDistributionResponse response = service.distribute(
                new FeedingDtos.FeedDistributionCreateRequest(feed.id, lotId, null, new BigDecimal("20"),
                        Instant.parse("2026-03-02T07:00:00Z"), null, null));

        assertThat(response.lotId()).isEqualTo(lotId);
        assertThat(stock.quantity).isEqualByComparingTo("30");
        verify(lotService).get(lotId);
        verify(auditService).record("FEED_DISTRIBUTED", "feed_distribution", response.id());
    }

    @Test
    void rejectsDistributionWhenStockInsufficient() {
        FeedEntity feed = feed();
        when(feedRepository.findByIdAndTenantId(feed.id, tenantId)).thenReturn(Optional.of(feed));
        FeedStockEntity stock = stock(new BigDecimal("5"), new BigDecimal("0.35"));
        when(stockRepository.findByTenantIdAndFeedId(tenantId, feed.id)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> service.distribute(new FeedingDtos.FeedDistributionCreateRequest(
                feed.id, lotId, null, new BigDecimal("20"), Instant.parse("2026-03-02T07:00:00Z"), null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsDistributionWithoutTarget() {
        FeedEntity feed = feed();
        when(feedRepository.findByIdAndTenantId(feed.id, tenantId)).thenReturn(Optional.of(feed));

        assertThatThrownBy(() -> service.distribute(new FeedingDtos.FeedDistributionCreateRequest(
                feed.id, null, null, new BigDecimal("2"), Instant.parse("2026-03-02T07:00:00Z"), null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    // P8-04

    @Test
    void listsAllFeedingPlansWhenNoSpecies() {
        when(speciesService.allFeedingPlans()).thenReturn(List.of(
                new ReferentialDtos.FeedingPlanResponse(UUID.randomUUID(), UUID.randomUUID(), "Poussin",
                        List.of(), true)));

        assertThat(service.listFeedingPlans(null)).hasSize(1);
    }

    @Test
    void listsFeedingPlansForSpecies() {
        UUID speciesId = UUID.randomUUID();
        when(speciesService.feedingPlans(speciesId)).thenReturn(List.of());

        assertThat(service.listFeedingPlans(speciesId)).isEmpty();
    }

    // P8-05

    @Test
    void listsStocksWithFeedName() {
        FeedEntity feed = feed();
        when(stockRepository.findByTenantId(tenantId))
                .thenReturn(List.of(stock(new BigDecimal("42"), new BigDecimal("0.35"))));
        when(feedRepository.findByIdAndTenantId(feed.id, tenantId)).thenReturn(Optional.of(feed));

        List<FeedingDtos.FeedStockResponse> stocks = service.listStocks();

        assertThat(stocks).hasSize(1);
        assertThat(stocks.get(0).feedName()).isEqualTo("Maïs");
        assertThat(stocks.get(0).quantity()).isEqualByComparingTo("42");
    }

    // P8-06

    @Test
    void computesFeedCostOverPeriod() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-31T23:59:59Z");
        FeedDistributionEntity distribution = new FeedDistributionEntity();
        distribution.id = UUID.randomUUID();
        distribution.feedId = feedId;
        distribution.lotId = lotId;
        distribution.quantity = new BigDecimal("20");
        when(distributionRepository.findByTenantIdAndLotIdAndDistributedAtBetweenOrderByDistributedAtDesc(
                tenantId, lotId, from, to)).thenReturn(List.of(distribution));
        when(stockRepository.findByTenantIdAndFeedId(tenantId, feedId))
                .thenReturn(Optional.of(stock(new BigDecimal("10"), new BigDecimal("0.35"))));

        FeedingDtos.FeedCostResponse response = service.computeCost(
                new FeedingDtos.FeedCostComputeRequest(lotId, null, from, to));

        assertThat(response.amount()).isEqualByComparingTo("7.00");
        assertThat(response.periodStart()).isEqualTo(LocalDate.parse("2026-03-01"));
        verify(costRepository).save(any(FeedCostEntity.class));
        verify(auditService).record("FEED_COST_COMPUTED", "feed_cost", response.id());
    }

    @Test
    void rejectsCostWithoutTarget() {
        assertThatThrownBy(() -> service.computeCost(new FeedingDtos.FeedCostComputeRequest(
                null, null, Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-31T00:00:00Z"))))
                .isInstanceOf(BusinessRuleException.class);
    }

    // P8-07

    @Test
    void flagsFeedsBelowThreshold() {
        FeedEntity feed = feed();
        when(stockRepository.findByTenantId(tenantId))
                .thenReturn(List.of(stock(new BigDecimal("8"), new BigDecimal("0.35"))));
        when(feedRepository.findByIdAndTenantId(feed.id, tenantId)).thenReturn(Optional.of(feed));

        List<FeedingDtos.LowStockAlertResponse> alerts = service.lowStock(new BigDecimal("10"));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).feedName()).isEqualTo("Maïs");
        assertThat(alerts.get(0).threshold()).isEqualByComparingTo("10");
    }

    @Test
    void ignoresFeedsAtOrAboveThreshold() {
        when(stockRepository.findByTenantId(tenantId))
                .thenReturn(List.of(stock(new BigDecimal("10"), new BigDecimal("0.35"))));

        assertThat(service.lowStock(new BigDecimal("10"))).isEmpty();
    }

    private FeedEntity feed() {
        FeedEntity feed = new FeedEntity();
        feed.id = feedId;
        feed.tenantId = tenantId;
        feed.name = "Maïs";
        feed.type = "CONCENTRATE";
        feed.unit = "kg";
        feed.price = new BigDecimal("0.35");
        return feed;
    }

    private FeedStockEntity stock(BigDecimal quantity, BigDecimal averagePrice) {
        FeedStockEntity stock = new FeedStockEntity();
        stock.id = UUID.randomUUID();
        stock.tenantId = tenantId;
        stock.feedId = feedId;
        stock.quantity = quantity;
        stock.averagePrice = averagePrice;
        return stock;
    }
}
