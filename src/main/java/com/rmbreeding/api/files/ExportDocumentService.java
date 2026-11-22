package com.rmbreeding.api.files;

import com.rmbreeding.api.finance.FinanceDtos;
import com.rmbreeding.api.finance.FinanceService;
import com.rmbreeding.api.health.HealthService;
import com.rmbreeding.api.herd.AnimalService;
import com.rmbreeding.api.herd.HerdDtos;
import com.rmbreeding.api.herd.LotDtos;
import com.rmbreeding.api.herd.LotService;
import com.rmbreeding.api.sales.SalesDtos;
import com.rmbreeding.api.sales.SalesService;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.PageResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ExportDocumentService {

    public static final String ANIMAL_SHEET = "ANIMAL_SHEET";
    public static final String HEALTH_BOOKLET = "HEALTH_BOOKLET";
    public static final String SALE_RECEIPT = "SALE_RECEIPT";
    public static final String SALES_REGISTER = "SALES_REGISTER";
    public static final String LOT_BALANCE = "LOT_BALANCE";
    public static final String FINANCIALS_XLSX = "FINANCIALS_XLSX";
    public static final String ANIMALS_XLSX = "ANIMALS_XLSX";
    public static final String LOTS_XLSX = "LOTS_XLSX";

    public static final Set<String> TYPES = Set.of(ANIMAL_SHEET, HEALTH_BOOKLET, SALE_RECEIPT,
            SALES_REGISTER, LOT_BALANCE, FINANCIALS_XLSX, ANIMALS_XLSX, LOTS_XLSX);

    private static final int MAX_ROWS = 5000;

    private final AnimalService animalService;
    private final LotService lotService;
    private final HealthService healthService;
    private final SalesService salesService;
    private final FinanceService financeService;

    public ExportDocumentService(AnimalService animalService, LotService lotService,
            HealthService healthService, SalesService salesService, FinanceService financeService) {
        this.animalService = animalService;
        this.lotService = lotService;
        this.healthService = healthService;
        this.salesService = salesService;
        this.financeService = financeService;
    }

    public ExportDocument render(String type, Map<String, Object> params) {
        String normalized = normalizeType(type);
        Map<String, Object> values = params == null ? Map.of() : params;
        return switch (normalized) {
            case ANIMAL_SHEET -> animalSheet(uuid(values, "animalId"));
            case HEALTH_BOOKLET -> healthBooklet(uuid(values, "animalId"));
            case SALE_RECEIPT -> saleReceipt(uuid(values, "saleId"));
            case SALES_REGISTER -> salesRegister(date(values, "from", firstDayOfMonth()),
                    date(values, "to", LocalDate.now()));
            case LOT_BALANCE -> lotBalance(uuid(values, "lotId"));
            case FINANCIALS_XLSX -> financials(date(values, "from", firstDayOfMonth()),
                    date(values, "to", LocalDate.now()));
            case ANIMALS_XLSX -> animals(string(values, "search"), string(values, "status"));
            case LOTS_XLSX -> lots(string(values, "status"));
            default -> throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Unsupported export type: " + type);
        };
    }

    private ExportDocument animalSheet(UUID animalId) {
        HerdDtos.AnimalResponse animal = animalService.get(animalId);
        byte[] content = PdfDocuments.animalSheet(animal);
        return ExportDocument.pdf("fiche-animal-" + slug(animal.identifier()) + ".pdf", content, 1);
    }

    private ExportDocument healthBooklet(UUID animalId) {
        HerdDtos.AnimalResponse animal = animalService.get(animalId);
        byte[] content = PdfDocuments.healthBooklet(animal, healthService.healthBook(animalId));
        return ExportDocument.pdf("carnet-sanitaire-" + slug(animal.identifier()) + ".pdf", content, 1);
    }

    private ExportDocument saleReceipt(UUID saleId) {
        SalesDtos.SaleResponse sale = salesService.getSale(saleId);
        byte[] content = PdfDocuments.saleReceipt(sale);
        return ExportDocument.pdf("recu-" + slug(sale.number()) + ".pdf", content, 1);
    }

    private ExportDocument salesRegister(LocalDate from, LocalDate to) {
        LocalDate end = to.isBefore(from) ? from : to;
        List<SalesDtos.SaleResponse> sales = salesService.listSales(null,
                from.atStartOfDay().toInstant(ZoneOffset.UTC),
                end.atTime(23, 59, 59).toInstant(ZoneOffset.UTC));
        byte[] content = PdfDocuments.salesRegister(sales, from, end);
        return ExportDocument.pdf("registre-ventes-" + from + "-" + end + ".pdf", content,
                sales.size());
    }

    private ExportDocument lotBalance(UUID lotId) {
        LotDtos.LotResponse lot = lotService.get(lotId);
        FinanceDtos.LotMarginResponse margin = financeService.lotMargin(lotId);
        BigDecimal mortalityRate = mortalityRate(lot);
        LotDtos.LotClosureResponse closure = new LotDtos.LotClosureResponse(lotId, mortalityRate,
                null, null, margin.cost(), margin.revenue(), margin.margin(), lot.closedAt());
        byte[] content = PdfDocuments.lotBalance(lot, closure);
        return ExportDocument.pdf("bilan-lot-" + slug(lot.code()) + ".pdf", content, 1);
    }

    private BigDecimal mortalityRate(LotDtos.LotResponse lot) {
        if (lot.initialCount() == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        int mortality = lotService.movements(lot.id()).stream()
                .filter(movement -> "MORTALITY".equals(movement.type()))
                .mapToInt(LotDtos.LotMovementResponse::quantity)
                .sum();
        return BigDecimal.valueOf(mortality)
                .divide(BigDecimal.valueOf(lot.initialCount()), 4, RoundingMode.HALF_UP);
    }

    private ExportDocument financials(LocalDate from, LocalDate to) {
        LocalDate end = to.isBefore(from) ? from : to;
        FinanceDtos.FinancialStatementResponse statement = financeService.financialStatement(from, end);
        List<FinanceDtos.ExpenseResponse> expenses = financeService.listExpenses(from, end);
        List<FinanceDtos.RevenueResponse> revenues = financeService.listRevenues(from, end);
        byte[] content = SpreadsheetDocuments.financials(statement, expenses, revenues);
        int rows = expenses.size() + revenues.size() + statement.revenues().size()
                + statement.expenses().size();
        return ExportDocument.xlsx("finances-" + from + "-" + end + ".xlsx", content, rows);
    }

    private ExportDocument animals(String search, String status) {
        List<HerdDtos.AnimalResponse> animals = new ArrayList<>();
        String cursor = null;
        while (animals.size() < MAX_ROWS) {
            PageResponse<HerdDtos.AnimalResponse> page = animalService.list(search, status, null,
                    null, null, 100, cursor);
            animals.addAll(page.items());
            cursor = page.nextCursor();
            if (cursor == null || !page.hasMore() || page.items().isEmpty()) {
                break;
            }
        }
        if (animals.size() > MAX_ROWS) {
            animals = animals.subList(0, MAX_ROWS);
        }
        byte[] content = SpreadsheetDocuments.animals(animals);
        return ExportDocument.xlsx("animaux-" + LocalDate.now() + ".xlsx", content, animals.size());
    }

    private ExportDocument lots(String status) {
        List<LotDtos.LotResponse> lots = new ArrayList<>();
        String cursor = null;
        while (lots.size() < MAX_ROWS) {
            PageResponse<LotDtos.LotResponse> page = lotService.list(status, null, 100, cursor);
            lots.addAll(page.items());
            cursor = page.nextCursor();
            if (cursor == null || !page.hasMore() || page.items().isEmpty()) {
                break;
            }
        }
        if (lots.size() > MAX_ROWS) {
            lots = lots.subList(0, MAX_ROWS);
        }
        byte[] content = SpreadsheetDocuments.lots(lots);
        return ExportDocument.xlsx("lots-" + LocalDate.now() + ".xlsx", content, lots.size());
    }

    public static String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalized)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Unsupported export type: " + type);
        }
        return normalized;
    }

    private static LocalDate firstDayOfMonth() {
        LocalDate today = LocalDate.now();
        return today.withDayOfMonth(1);
    }

    private static UUID uuid(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Missing parameter: " + key);
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Invalid UUID for parameter: " + key);
        }
    }

    private static LocalDate date(Map<String, Object> params, String key, LocalDate fallback) {
        Object value = params.get(key);
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (RuntimeException ex) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Invalid date for parameter: " + key);
        }
    }

    private static String string(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private static String slug(String value) {
        if (value == null || value.isBlank()) {
            return "document";
        }
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "document" : slug;
    }
}
