package com.rmbreeding.api.reporting;

import com.rmbreeding.api.finance.FinanceDtos;
import com.rmbreeding.api.finance.FinanceService;
import com.rmbreeding.api.feeding.FeedingDtos;
import com.rmbreeding.api.feeding.FeedingService;
import com.rmbreeding.api.health.HealthDtos;
import com.rmbreeding.api.health.HealthService;
import com.rmbreeding.api.herd.AnimalService;
import com.rmbreeding.api.herd.LotDtos;
import com.rmbreeding.api.herd.LotService;
import com.rmbreeding.api.production.ProductionDtos;
import com.rmbreeding.api.production.ProductionService;
import com.rmbreeding.api.reproduction.ReproductionService;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndicatorServiceTest {

    @Mock
    private AnimalService animalService;
    @Mock
    private LotService lotService;
    @Mock
    private ReproductionService reproductionService;
    @Mock
    private HealthService healthService;
    @Mock
    private FeedingService feedingService;
    @Mock
    private ProductionService productionService;
    @Mock
    private FinanceService financeService;

    private IndicatorService service;

    private final LocalDate from = LocalDate.parse("2026-11-01");
    private final LocalDate to = LocalDate.parse("2026-11-30");

    @BeforeEach
    void setUp() {
        service = new IndicatorService(new IndicatorRegistry(), animalService, lotService,
                reproductionService, healthService, feedingService, productionService, financeService);
    }

    @Test
    void listsAllRegisteredIndicators() {
        when(animalService.countActive()).thenReturn(42L);
        when(lotService.openCount()).thenReturn(3L);
        when(reproductionService.countBirths(from, to)).thenReturn(5L);
        when(reproductionService.countMatings(from, to)).thenReturn(10L);
        when(reproductionService.countPositiveDiagnoses(from, to)).thenReturn(6L);
        when(healthService.listMortalities(isNull())).thenReturn(List.of());
        when(healthService.listWithdrawals("ACTIVE")).thenReturn(List.of());
        when(feedingService.listStocks()).thenReturn(List.of());
        when(feedingService.lowStock(isNull())).thenReturn(List.of());
        when(lotService.list("OPEN", null, 100, null)).thenReturn(PageResponse.last(List.of()));
        when(productionService.listEggStocks()).thenReturn(List.of());
        when(financeService.financialStatement(from, to)).thenReturn(statement());

        List<ReportingDtos.IndicatorResponse> indicators = service.list(from, to);

        assertThat(indicators).hasSize(14);
        ReportingDtos.IndicatorResponse herdSize = indicators.stream()
                .filter(i -> i.code().equals("HERD_SIZE")).findFirst().orElseThrow();
        assertThat(herdSize.value()).isEqualByComparingTo("42");
        ReportingDtos.IndicatorResponse pregnancy = indicators.stream()
                .filter(i -> i.code().equals("PREGNANCY_RATE")).findFirst().orElseThrow();
        assertThat(pregnancy.value()).isEqualByComparingTo("60.0");
    }

    @Test
    void pregnancyRateIsNullWithoutMatings() {
        when(reproductionService.countMatings(from, to)).thenReturn(0L);

        ReportingDtos.IndicatorResponse response = service.get("PREGNANCY_RATE", from, to);

        assertThat(response.value()).isNull();
    }

    @Test
    void sumsMortalitiesWithinPeriod() {
        Instant inside = from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Instant outside = to.plusDays(5).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        when(healthService.listMortalities(null)).thenReturn(List.of(
                mortality(3, inside),
                mortality(2, outside)));

        ReportingDtos.IndicatorResponse response = service.get("MORTALITIES_PERIOD", from, to);

        assertThat(response.value()).isEqualByComparingTo("3");
    }

    @Test
    void computesFeedStockValue() {
        when(feedingService.listStocks()).thenReturn(List.of(
                stock(new BigDecimal("10"), new BigDecimal("250")),
                stock(new BigDecimal("4"), new BigDecimal("500"))));

        ReportingDtos.IndicatorResponse response = service.get("FEED_STOCK_VALUE", from, to);

        assertThat(response.value()).isEqualByComparingTo("4500.00");
    }

    @Test
    void averagesDailyGainAcrossOpenLots() {
        UUID lotA = UUID.randomUUID();
        UUID lotB = UUID.randomUUID();
        when(lotService.list("OPEN", null, 100, null)).thenReturn(PageResponse.last(List.of(
                lot(lotA, null), lot(lotB, null))));
        when(productionService.gmq(null, lotA)).thenReturn(gmq(new BigDecimal("0.8")));
        when(productionService.gmq(null, lotB)).thenReturn(gmq(new BigDecimal("1.0")));

        ReportingDtos.IndicatorResponse response = service.get("AVERAGE_DAILY_GAIN", from, to);

        assertThat(response.value()).isEqualByComparingTo("0.90");
    }

    @Test
    void layingRateOnlyConsidersLayerLots() {
        UUID layerLot = UUID.randomUUID();
        UUID beefLot = UUID.randomUUID();
        when(lotService.list("OPEN", null, 100, null)).thenReturn(PageResponse.last(List.of(
                lot(layerLot, "LAYER"), lot(beefLot, "BEEF"))));
        when(productionService.layingRate(eq(layerLot), any(Instant.class), any(Instant.class)))
                .thenReturn(layingRate(new BigDecimal("80")));

        ReportingDtos.IndicatorResponse response = service.get("LAYING_RATE", from, to);

        assertThat(response.value()).isEqualByComparingTo("80.00");
    }

    @Test
    void averageDailyGainIsNullWithoutWeighings() {
        UUID lotId = UUID.randomUUID();
        when(lotService.list("OPEN", null, 100, null))
                .thenReturn(PageResponse.last(List.of(lot(lotId, null))));
        when(productionService.gmq(isNull(), eq(lotId)))
                .thenThrow(new NotFoundException("At least two weighings are required to compute GMQ"));

        ReportingDtos.IndicatorResponse response = service.get("AVERAGE_DAILY_GAIN", from, to);

        assertThat(response.value()).isNull();
    }

    @Test
    void layingRateIsNullWithoutHens() {
        UUID lotId = UUID.randomUUID();
        when(lotService.list("OPEN", null, 100, null))
                .thenReturn(PageResponse.last(List.of(lot(lotId, "LAYER"))));
        when(productionService.layingRate(eq(lotId), any(Instant.class), any(Instant.class)))
                .thenReturn(layingRate(BigDecimal.ZERO, 0));

        ReportingDtos.IndicatorResponse response = service.get("LAYING_RATE", from, to);

        assertThat(response.value()).isNull();
    }

    @Test
    void sumsEggStock() {
        when(productionService.listEggStocks()).thenReturn(List.of(
                eggStock(120), eggStock(30)));

        ReportingDtos.IndicatorResponse response = service.get("EGG_STOCK", from, to);

        assertThat(response.value()).isEqualByComparingTo("150");
    }

    @Test
    void exposesFinanceIndicators() {
        when(financeService.financialStatement(from, to)).thenReturn(statement());

        assertThat(service.get("REVENUE_PERIOD", from, to).value()).isEqualByComparingTo("5000");
        assertThat(service.get("EXPENSE_PERIOD", from, to).value()).isEqualByComparingTo("2000");
        assertThat(service.get("NET_MARGIN", from, to).value()).isEqualByComparingTo("3000");
    }

    @Test
    void reportsUnavailableIndicatorsAsNullWhenNoData() {
        when(animalService.countActive()).thenReturn(0L);
        when(lotService.openCount()).thenReturn(0L);
        when(reproductionService.countBirths(from, to)).thenReturn(0L);
        when(reproductionService.countMatings(from, to)).thenReturn(0L);
        when(healthService.listMortalities(isNull())).thenReturn(List.of());
        when(healthService.listWithdrawals("ACTIVE")).thenReturn(List.of());
        when(feedingService.listStocks()).thenReturn(List.of());
        when(feedingService.lowStock(isNull())).thenReturn(List.of());
        when(lotService.list("OPEN", null, 100, null)).thenReturn(PageResponse.last(List.of()));
        when(productionService.listEggStocks()).thenReturn(List.of());
        when(financeService.financialStatement(from, to)).thenReturn(emptyStatement());

        Map<String, BigDecimal> values = new HashMap<>();
        service.list(from, to).forEach(indicator -> values.put(indicator.code(), indicator.value()));

        assertThat(values).hasSize(14);
        assertThat(values.get("HERD_SIZE")).isEqualByComparingTo("0");
        assertThat(values.get("PREGNANCY_RATE")).isNull();
        assertThat(values.get("AVERAGE_DAILY_GAIN")).isNull();
        assertThat(values.get("LAYING_RATE")).isNull();
        assertThat(values.get("REVENUE_PERIOD")).isEqualByComparingTo("0");
        assertThat(values.get("EXPENSE_PERIOD")).isEqualByComparingTo("0");
        assertThat(values.get("NET_MARGIN")).isEqualByComparingTo("0");
    }

    @Test
    void rejectsUnknownIndicator() {
        assertThatThrownBy(() -> service.get("NOPE", from, to))
                .isInstanceOf(NotFoundException.class);
    }

    private HealthDtos.MortalityResponse mortality(int count, Instant occurredAt) {
        return new HealthDtos.MortalityResponse(UUID.randomUUID(), null, null, count, "cause",
                occurredAt, null);
    }

    private FeedingDtos.FeedStockResponse stock(BigDecimal quantity, BigDecimal price) {
        return new FeedingDtos.FeedStockResponse(UUID.randomUUID(), "Maïs", "KG", quantity, price, null);
    }

    private ProductionDtos.EggStockResponse eggStock(int quantity) {
        return new ProductionDtos.EggStockResponse(UUID.randomUUID(), UUID.randomUUID(), quantity, null);
    }

    private ProductionDtos.GmqResponse gmq(BigDecimal value) {
        return new ProductionDtos.GmqResponse(null, null, null, null, 30, null, null, value);
    }

    private ProductionDtos.LayingRateResponse layingRate(BigDecimal value) {
        return layingRate(value, 100);
    }

    private ProductionDtos.LayingRateResponse layingRate(BigDecimal value, int henCount) {
        return new ProductionDtos.LayingRateResponse(null, null, null, 30, henCount, 80, value);
    }

    private LotDtos.LotResponse lot(UUID id, String productionType) {
        return new LotDtos.LotResponse(id, UUID.randomUUID(), UUID.randomUUID(), "Lot", null,
                productionType, "OPEN", from, 10, 10, null, null, null, 1, Instant.now(), Instant.now());
    }

    private FinanceDtos.FinancialStatementResponse statement() {
        return new FinanceDtos.FinancialStatementResponse(from, to, new BigDecimal("5000"),
                new BigDecimal("2000"), new BigDecimal("3000"), List.of(), List.of());
    }

    private FinanceDtos.FinancialStatementResponse emptyStatement() {
        return new FinanceDtos.FinancialStatementResponse(from, to, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of());
    }
}
