package com.rmbreeding.api.alerts;

import com.rmbreeding.api.feeding.FeedingDtos;
import com.rmbreeding.api.feeding.FeedingService;
import com.rmbreeding.api.herd.LotDtos;
import com.rmbreeding.api.herd.LotService;
import com.rmbreeding.api.production.ProductionDtos;
import com.rmbreeding.api.production.ProductionService;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

// P11-04 Stock, expiry and laying alert generators
@Component
class FeedProductionAlertGenerator implements AlertGenerator {

    private static final BigDecimal LAYING_DROP_THRESHOLD = BigDecimal.valueOf(50);

    private final FeedingService feedingService;
    private final ProductionService productionService;
    private final LotService lotService;
    private final AlertsService alertsService;

    FeedProductionAlertGenerator(FeedingService feedingService, ProductionService productionService,
            LotService lotService, AlertsService alertsService) {
        this.feedingService = feedingService;
        this.productionService = productionService;
        this.lotService = lotService;
        this.alertsService = alertsService;
    }

    @Override
    public void generate(LocalDate today) {
        UUID tenantId = TenantContext.requireTenantId();
        lowStock(tenantId, today);
        expiry(tenantId, today);
        layingDrop(tenantId, today);
    }

    private void lowStock(UUID tenantId, LocalDate today) {
        UUID farmId = alertsService.requireDefaultFarmId();
        for (FeedingDtos.LowStockAlertResponse low : feedingService.lowStock(null)) {
            alertsService.raise(tenantId, farmId, "LOW_STOCK", "IMPORTANT",
                    "Stock bas",
                    "Stock bas pour " + low.feedName() + " (" + low.quantity() + " " + low.unit() + ")",
                    today, "feed", low.feedId());
        }
    }

    private void expiry(UUID tenantId, LocalDate today) {
        UUID farmId = alertsService.requireDefaultFarmId();
        for (FeedingDtos.FeedReceiptResponse receipt : feedingService.expiringReceipts(today.plusDays(30))) {
            alertsService.raise(tenantId, farmId, "FEED_EXPIRY", "IMPORTANT",
                    "Péremption d'aliment",
                    "Un lot d'aliment périme le " + receipt.expiryDate(),
                    receipt.expiryDate(), "feed", receipt.feedId());
        }
    }

    private void layingDrop(UUID tenantId, LocalDate today) {
        for (LotDtos.LotResponse lot : lotService.list("OPEN", null, 100, null).items()) {
            if (!isLaying(lot.productionType())) {
                continue;
            }
            ProductionDtos.LayingRateResponse rate = productionService.layingRate(lot.id(),
                    today.minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant(),
                    today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
            if (rate.henCount() > 0 && rate.layingRate() != null
                    && rate.layingRate().compareTo(LAYING_DROP_THRESHOLD) < 0) {
                alertsService.raise(tenantId, lot.farmId(), "LAYING_DROP", "IMPORTANT",
                        "Baisse de ponte",
                        "Taux de ponte " + rate.layingRate() + "% sur 3 jours pour le lot " + lot.name(),
                        today, "lot", lot.id());
            }
        }
    }

    private boolean isLaying(String productionType) {
        if (productionType == null) {
            return false;
        }
        String value = productionType.toUpperCase(Locale.ROOT);
        return value.contains("LAY") || value.contains("EGG") || value.contains("PONTE")
                || value.contains("POULTRY") || value.contains("VOLAILLE");
    }
}
