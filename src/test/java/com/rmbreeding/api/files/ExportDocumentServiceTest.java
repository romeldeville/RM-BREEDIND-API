package com.rmbreeding.api.files;

import com.rmbreeding.api.finance.FinanceDtos;
import com.rmbreeding.api.finance.FinanceService;
import com.rmbreeding.api.health.HealthDtos;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
class ExportDocumentServiceTest {

    @Mock
    private AnimalService animalService;
    @Mock
    private LotService lotService;
    @Mock
    private HealthService healthService;
    @Mock
    private SalesService salesService;
    @Mock
    private FinanceService financeService;

    private ExportDocumentService service;

    @BeforeEach
    void setUp() {
        service = new ExportDocumentService(animalService, lotService, healthService, salesService,
                financeService);
    }

    @Test
    void rendersAnimalSheetPdf() {
        UUID animalId = UUID.randomUUID();
        when(animalService.get(animalId)).thenReturn(animal(animalId));

        ExportDocument document = service.render(ExportDocumentService.ANIMAL_SHEET,
                Map.of("animalId", animalId.toString()));

        assertThat(document.contentType()).isEqualTo("application/pdf");
        assertThat(document.filename()).isEqualTo("fiche-animal-bov-001.pdf");
        assertThat(new String(document.content(), 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void rendersHealthBookletPdf() {
        UUID animalId = UUID.randomUUID();
        when(animalService.get(animalId)).thenReturn(animal(animalId));
        when(healthService.healthBook(animalId))
                .thenReturn(new HealthDtos.HealthBookResponse(animalId, List.of(), List.of(),
                        List.of(), List.of(), List.of()));

        ExportDocument document = service.render(ExportDocumentService.HEALTH_BOOKLET,
                Map.of("animalId", animalId.toString()));

        assertThat(document.filename()).isEqualTo("carnet-sanitaire-bov-001.pdf");
        assertThat(new String(document.content(), 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void rendersAnimalsSpreadsheet() {
        when(animalService.list(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResponse.last(List.of(animal(UUID.randomUUID()))));

        ExportDocument document = service.render(ExportDocumentService.ANIMALS_XLSX, Map.of());

        assertThat(document.contentType()).contains("spreadsheetml");
        assertThat(document.rowCount()).isEqualTo(1);
        assertThat(document.content()[0]).isEqualTo((byte) 'P');
        assertThat(document.content()[1]).isEqualTo((byte) 'K');
    }

    @Test
    void rejectsUnknownType() {
        assertThatThrownBy(() -> service.render("UNKNOWN", Map.of()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void rejectsMissingParameter() {
        assertThatThrownBy(() -> service.render(ExportDocumentService.ANIMAL_SHEET, Map.of()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void rejectsInvalidUuidParameter() {
        assertThatThrownBy(() -> service.render(ExportDocumentService.ANIMAL_SHEET,
                Map.of("animalId", "not-a-uuid")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rendersSaleReceiptPdf() {
        UUID saleId = UUID.randomUUID();
        when(salesService.getSale(saleId)).thenReturn(sale(saleId));

        ExportDocument document = service.render(ExportDocumentService.SALE_RECEIPT,
                Map.of("saleId", saleId.toString()));

        assertThat(document.filename()).isEqualTo("recu-vte-2026-001.pdf");
        assertThat(new String(document.content(), 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void rendersSalesRegisterPdf() {
        when(salesService.listSales(isNull(), any(), any()))
                .thenReturn(List.of(sale(UUID.randomUUID())));

        ExportDocument document = service.render(ExportDocumentService.SALES_REGISTER,
                Map.of("from", "2026-03-01", "to", "2026-03-31"));

        assertThat(document.filename()).isEqualTo("registre-ventes-2026-03-01-2026-03-31.pdf");
        assertThat(document.rowCount()).isEqualTo(1);
        assertThat(new String(document.content(), 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void rendersLotBalanceWithMortalityRate() {
        UUID lotId = UUID.randomUUID();
        when(lotService.get(lotId)).thenReturn(lot(lotId, 10));
        when(lotService.movements(lotId)).thenReturn(List.of(
                movement(lotId, "MORTALITY", 2),
                movement(lotId, "ENTRY", 5)));
        when(financeService.lotMargin(lotId)).thenReturn(new FinanceDtos.LotMarginResponse(lotId,
                new BigDecimal("1000"), new BigDecimal("600"), new BigDecimal("400"),
                new BigDecimal("0.4")));

        ExportDocument document = service.render(ExportDocumentService.LOT_BALANCE,
                Map.of("lotId", lotId.toString()));

        assertThat(document.filename()).isEqualTo("bilan-lot-lot-01.pdf");
        assertThat(new String(document.content(), 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void rendersLotBalanceWithoutInitialHeadcount() {
        UUID lotId = UUID.randomUUID();
        when(lotService.get(lotId)).thenReturn(lot(lotId, 0));
        when(financeService.lotMargin(lotId)).thenReturn(new FinanceDtos.LotMarginResponse(lotId,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        ExportDocument document = service.render(ExportDocumentService.LOT_BALANCE,
                Map.of("lotId", lotId.toString()));

        assertThat(document.rowCount()).isEqualTo(1);
    }

    @Test
    void rendersFinancialsWorkbook() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        when(financeService.financialStatement(from, to)).thenReturn(
                new FinanceDtos.FinancialStatementResponse(from, to, new BigDecimal("1000"),
                        new BigDecimal("600"), new BigDecimal("400"), List.of(), List.of()));
        when(financeService.listExpenses(from, to)).thenReturn(List.of(expense()));
        when(financeService.listRevenues(from, to)).thenReturn(List.of(revenue()));

        ExportDocument document = service.render(ExportDocumentService.FINANCIALS_XLSX,
                Map.of("from", from.toString(), "to", to.toString()));

        assertThat(document.filename()).isEqualTo("finances-2026-03-01-2026-03-31.xlsx");
        assertThat(document.rowCount()).isEqualTo(2);
        assertThat(document.contentType()).contains("spreadsheetml");
    }

    @Test
    void rendersLotsSpreadsheet() {
        when(lotService.list(eq("OPEN"), isNull(), any(), isNull()))
                .thenReturn(PageResponse.last(List.of(lot(UUID.randomUUID(), 12))));

        ExportDocument document = service.render(ExportDocumentService.LOTS_XLSX,
                Map.of("status", "OPEN"));

        assertThat(document.contentType()).contains("spreadsheetml");
        assertThat(document.rowCount()).isEqualTo(1);
        assertThat(document.content()[0]).isEqualTo((byte) 'P');
    }

    @Test
    void rejectsInvalidDateParameter() {
        assertThatThrownBy(() -> service.render(ExportDocumentService.SALES_REGISTER,
                Map.of("from", "31/03/2026")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    private HerdDtos.AnimalResponse animal(UUID animalId) {
        return new HerdDtos.AnimalResponse(animalId, UUID.randomUUID(), UUID.randomUUID(), null,
                null, "BOV-001", "Bella", "F", LocalDate.of(2022, 3, 12), false, "BORN",
                "ACTIVE", null, null, null, new BigDecimal("452.5"), LocalDate.of(2022, 3, 12),
                null, Instant.now(), Instant.now(), 1);
    }

    private SalesDtos.SaleResponse sale(UUID saleId) {
        return new SalesDtos.SaleResponse(saleId, UUID.randomUUID(), null, "VTE-2026-001",
                "ANIMAL", "COMPLETED", Instant.parse("2026-03-15T10:00:00Z"),
                new BigDecimal("150000"), "XOF", "PAID", null, null, List.of());
    }

    private LotDtos.LotResponse lot(UUID lotId, int initialCount) {
        return new LotDtos.LotResponse(lotId, UUID.randomUUID(), UUID.randomUUID(), "Lot test",
                "LOT-01", "MEAT", initialCount == 0 ? "CLOSED" : "OPEN",
                LocalDate.of(2026, 1, 1), initialCount, initialCount, "BORN",
                new BigDecimal("1000"), null, 1, Instant.now(), Instant.now());
    }

    private LotDtos.LotMovementResponse movement(UUID lotId, String type, int quantity) {
        return new LotDtos.LotMovementResponse(UUID.randomUUID(), lotId, type, quantity,
                Instant.parse("2026-03-01T10:00:00Z"), null, UUID.randomUUID(), Instant.now());
    }

    private FinanceDtos.ExpenseResponse expense() {
        return new FinanceDtos.ExpenseResponse(UUID.randomUUID(), UUID.randomUUID(), "FEED",
                new BigDecimal("250"), "XOF", LocalDate.of(2026, 3, 10), "Aliment",
                "Fournisseur");
    }

    private FinanceDtos.RevenueResponse revenue() {
        return new FinanceDtos.RevenueResponse(UUID.randomUUID(), UUID.randomUUID(), "SALES",
                new BigDecimal("1000"), "XOF", LocalDate.of(2026, 3, 12), "Ventes");
    }
}
