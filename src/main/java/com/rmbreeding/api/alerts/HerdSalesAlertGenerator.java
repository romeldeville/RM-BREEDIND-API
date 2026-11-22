package com.rmbreeding.api.alerts;

import com.rmbreeding.api.herd.LotDtos;
import com.rmbreeding.api.herd.LotService;
import com.rmbreeding.api.identity.FarmService;
import com.rmbreeding.api.identity.IdentityDtos;
import com.rmbreeding.api.production.ProductionDtos;
import com.rmbreeding.api.production.ProductionService;
import com.rmbreeding.api.referential.ReferentialDtos;
import com.rmbreeding.api.referential.SiteService;
import com.rmbreeding.api.sales.SalesDtos;
import com.rmbreeding.api.sales.SalesService;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

// P11-05 Weighing, overcrowding and payment alert generators
@Component
class HerdSalesAlertGenerator implements AlertGenerator {

    private static final int WEIGHING_DUE_DAYS = 30;
    private static final int PAYMENT_DUE_DAYS = 30;

    private final LotService lotService;
    private final ProductionService productionService;
    private final SiteService siteService;
    private final SalesService salesService;
    private final FarmService farmService;
    private final AlertsService alertsService;

    HerdSalesAlertGenerator(LotService lotService, ProductionService productionService,
            SiteService siteService, SalesService salesService, FarmService farmService,
            AlertsService alertsService) {
        this.lotService = lotService;
        this.productionService = productionService;
        this.siteService = siteService;
        this.salesService = salesService;
        this.farmService = farmService;
        this.alertsService = alertsService;
    }

    @Override
    public void generate(LocalDate today) {
        UUID tenantId = TenantContext.requireTenantId();
        weighingDue(tenantId, today);
        overcrowding(tenantId, today);
        paymentOverdue(tenantId, today);
    }

    private void weighingDue(UUID tenantId, LocalDate today) {
        for (LotDtos.LotResponse lot : lotService.list("OPEN", null, 100, null).items()) {
            List<ProductionDtos.WeighingResponse> weighings = productionService.listWeighings(null, lot.id());
            Instant last = weighings.stream()
                    .map(ProductionDtos.WeighingResponse::weighedAt)
                    .filter(java.util.Objects::nonNull)
                    .max(Instant::compareTo)
                    .orElse(null);
            LocalDate due = today.minusDays(WEIGHING_DUE_DAYS);
            boolean stale = last == null || last.atZone(ZoneOffset.UTC).toLocalDate().isBefore(due);
            if (stale) {
                alertsService.raise(tenantId, lot.farmId(), "WEIGHING_DUE", "INFO",
                        "Pesée due",
                        "Aucune pesée récente pour le lot " + lot.name(),
                        today, "lot", lot.id());
            }
        }
    }

    private void overcrowding(UUID tenantId, LocalDate today) {
        for (IdentityDtos.FarmResponse farm : farmService.list()) {
            for (ReferentialDtos.BuildingResponse building : siteService.buildings(farm.id())) {
                for (ReferentialDtos.PenResponse pen : siteService.pens(building.id())) {
                    ReferentialDtos.PenOccupancyResponse occupancy = siteService.occupancy(pen.id());
                    if (occupancy.capacity() > 0 && occupancy.occupied() > occupancy.capacity()) {
                        alertsService.raise(tenantId, pen.farmId(), "OVERCROWDING", "IMPORTANT",
                                "Surpeuplement",
                                "Occupation " + occupancy.occupied() + "/" + occupancy.capacity()
                                        + " dans " + pen.name(),
                                today, "pen", pen.id());
                    }
                }
            }
        }
    }

    private void paymentOverdue(UUID tenantId, LocalDate today) {
        for (SalesDtos.SaleResponse sale : salesService.listSales("CONFIRMED", null, null)) {
            if ("PAID".equals(sale.paymentStatus()) || sale.soldAt() == null) {
                continue;
            }
            LocalDate soldDate = sale.soldAt().atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate due = soldDate.plusDays(PAYMENT_DUE_DAYS);
            if (due.isBefore(today)) {
                alertsService.raise(tenantId, sale.farmId(), "PAYMENT_OVERDUE", "IMPORTANT",
                        "Paiement en retard",
                        "Paiement en attente pour la vente " + sale.number(),
                        due, "sale", sale.id());
            }
        }
    }
}
