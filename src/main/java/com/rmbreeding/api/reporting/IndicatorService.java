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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class IndicatorService {

    private static final Set<String> LAYER_KEYWORDS =
            Set.of("LAY", "EGG", "PONTE", "POULTRY", "VOLAILLE", "POULE");

    private final IndicatorRegistry registry;
    private final AnimalService animalService;
    private final LotService lotService;
    private final ReproductionService reproductionService;
    private final HealthService healthService;
    private final FeedingService feedingService;
    private final ProductionService productionService;
    private final FinanceService financeService;

    public IndicatorService(IndicatorRegistry registry,
            AnimalService animalService,
            LotService lotService,
            ReproductionService reproductionService,
            HealthService healthService,
            FeedingService feedingService,
            ProductionService productionService,
            FinanceService financeService) {
        this.registry = registry;
        this.animalService = animalService;
        this.lotService = lotService;
        this.reproductionService = reproductionService;
        this.healthService = healthService;
        this.feedingService = feedingService;
        this.productionService = productionService;
        this.financeService = financeService;
    }

    public List<ReportingDtos.IndicatorResponse> list(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        List<ReportingDtos.IndicatorResponse> responses = new ArrayList<>();
        for (IndicatorDefinition definition : registry.all()) {
            responses.add(new ReportingDtos.IndicatorResponse(definition.code(), definition.label(),
                    definition.unit(), compute(definition.code(), start, end), start, end));
        }
        return responses;
    }

    public ReportingDtos.IndicatorResponse get(String code, LocalDate from, LocalDate to) {
        IndicatorDefinition definition = registry.find(code)
                .orElseThrow(() -> new NotFoundException("Unknown indicator: " + code));
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        return new ReportingDtos.IndicatorResponse(definition.code(), definition.label(),
                definition.unit(), compute(code, start, end), start, end);
    }

    BigDecimal compute(String code, LocalDate from, LocalDate to) {
        return switch (code) {
            case "HERD_SIZE" -> BigDecimal.valueOf(animalService.countActive());
            case "OPEN_LOTS" -> BigDecimal.valueOf(lotService.openCount());
            case "BIRTHS_PERIOD" -> BigDecimal.valueOf(reproductionService.countBirths(from, to));
            case "PREGNANCY_RATE" -> pregnancyRate(from, to);
            case "MORTALITIES_PERIOD" -> BigDecimal.valueOf(mortalities(from, to));
            case "ACTIVE_WITHDRAWALS" -> BigDecimal.valueOf(activeWithdrawals());
            case "FEED_STOCK_VALUE" -> feedStockValue();
            case "LOW_STOCK_COUNT" -> BigDecimal.valueOf(lowStockCount());
            case "AVERAGE_DAILY_GAIN" -> averageDailyGain();
            case "LAYING_RATE" -> layingRate(from, to);
            case "EGG_STOCK" -> BigDecimal.valueOf(eggStock());
            case "REVENUE_PERIOD" -> financialStatement(from, to).totalRevenue();
            case "EXPENSE_PERIOD" -> financialStatement(from, to).totalExpense();
            case "NET_MARGIN" -> financialStatement(from, to).net();
            default -> null;
        };
    }

    BigDecimal herdSize() {
        return BigDecimal.valueOf(animalService.countActive());
    }

    BigDecimal birthsPeriod(LocalDate from, LocalDate to) {
        return BigDecimal.valueOf(reproductionService.countBirths(from, to));
    }

    BigDecimal pregnancyRate(LocalDate from, LocalDate to) {
        long matings = reproductionService.countMatings(from, to);
        if (matings == 0) {
            return null;
        }
        long positive = reproductionService.countPositiveDiagnoses(from, to);
        return BigDecimal.valueOf(positive)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(matings), 1, RoundingMode.HALF_UP);
    }

    long mortalities(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to.atTime(23, 59, 59).atOffset(ZoneOffset.UTC).toInstant();
        return healthService.listMortalities(null).stream()
                .filter(mortality -> mortality.occurredAt() != null
                        && !mortality.occurredAt().isBefore(start)
                        && !mortality.occurredAt().isAfter(end))
                .mapToLong(HealthDtos.MortalityResponse::count)
                .sum();
    }

    long activeWithdrawals() {
        return healthService.listWithdrawals("ACTIVE").size();
    }

    BigDecimal feedStockValue() {
        BigDecimal total = BigDecimal.ZERO;
        for (FeedingDtos.FeedStockResponse stock : feedingService.listStocks()) {
            BigDecimal quantity = stock.quantity() == null ? BigDecimal.ZERO : stock.quantity();
            BigDecimal price = stock.averagePrice() == null ? BigDecimal.ZERO : stock.averagePrice();
            total = total.add(quantity.multiply(price));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    long lowStockCount() {
        return feedingService.lowStock(null).size();
    }

    BigDecimal averageDailyGain() {
        List<BigDecimal> values = new ArrayList<>();
        for (LotDtos.LotResponse lot : openLots()) {
            try {
                ProductionDtos.GmqResponse gmq = productionService.gmq(null, lot.id());
                if (gmq != null && gmq.gmq() != null) {
                    values.add(gmq.gmq());
                }
            } catch (RuntimeException ignored) {
                // insufficient data for this lot
            }
        }
        return average(values);
    }

    BigDecimal layingRate(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to.atTime(23, 59, 59).atOffset(ZoneOffset.UTC).toInstant();
        List<BigDecimal> values = new ArrayList<>();
        for (LotDtos.LotResponse lot : openLots()) {
            if (!isLayerLot(lot)) {
                continue;
            }
            try {
                ProductionDtos.LayingRateResponse rate = productionService.layingRate(lot.id(), start, end);
                if (rate != null && rate.henCount() > 0 && rate.layingRate() != null) {
                    values.add(rate.layingRate());
                }
            } catch (RuntimeException ignored) {
                // no collections for this lot
            }
        }
        return average(values);
    }

    long eggStock() {
        return productionService.listEggStocks().stream()
                .mapToLong(ProductionDtos.EggStockResponse::quantity)
                .sum();
    }

    BigDecimal netMargin(LocalDate from, LocalDate to) {
        return financialStatement(from, to).net();
    }

    private FinanceDtos.FinancialStatementResponse financialStatement(LocalDate from, LocalDate to) {
        return financeService.financialStatement(from, to);
    }

    private List<LotDtos.LotResponse> openLots() {
        return lotService.list("OPEN", null, 100, null).items();
    }

    private boolean isLayerLot(LotDtos.LotResponse lot) {
        String type = lot.productionType();
        if (type == null) {
            return false;
        }
        String upper = type.toUpperCase(Locale.ROOT);
        return LAYER_KEYWORDS.stream().anyMatch(upper::contains);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
}
