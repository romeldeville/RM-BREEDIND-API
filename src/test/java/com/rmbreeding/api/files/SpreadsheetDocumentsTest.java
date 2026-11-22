package com.rmbreeding.api.files;

import com.rmbreeding.api.finance.FinanceDtos;
import com.rmbreeding.api.herd.HerdDtos;
import com.rmbreeding.api.herd.LotDtos;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpreadsheetDocumentsTest {

    @Test
    void rendersAnimalsWorkbook() throws Exception {
        HerdDtos.AnimalResponse animal = new HerdDtos.AnimalResponse(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), null, null, "BOV-001", "Bella", "F",
                LocalDate.of(2022, 3, 12), false, "BORN", "ACTIVE", null, null, null,
                new BigDecimal("452.5"), LocalDate.of(2022, 3, 12), null, Instant.now(),
                Instant.now(), 1);

        byte[] content = SpreadsheetDocuments.animals(List.of(animal));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("Animaux");
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("BOV-001");
        }
    }

    @Test
    void rendersLotsWorkbook() throws Exception {
        LotDtos.LotResponse lot = new LotDtos.LotResponse(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Lot poulets", "LOT-001", "MEAT", "OPEN",
                LocalDate.now().minusDays(30), 500, 480, "Achat", new BigDecimal("1200"), null, 1,
                Instant.now(), Instant.now());

        byte[] content = SpreadsheetDocuments.lots(List.of(lot));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("Lots");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("LOT-001");
        }
    }

    @Test
    void rendersFinancialWorkbookWithThreeSheets() throws Exception {
        FinanceDtos.FinancialStatementResponse statement =
                new FinanceDtos.FinancialStatementResponse(LocalDate.now().minusDays(30),
                        LocalDate.now(), new BigDecimal("900000"), new BigDecimal("600000"),
                        new BigDecimal("300000"),
                        List.of(new FinanceDtos.FinancialStatementLine("VENTES",
                                new BigDecimal("900000"))),
                        List.of(new FinanceDtos.FinancialStatementLine("ALIMENT",
                                new BigDecimal("600000"))));
        FinanceDtos.ExpenseResponse expense = new FinanceDtos.ExpenseResponse(UUID.randomUUID(),
                UUID.randomUUID(), "ALIMENT", new BigDecimal("600000"), "XOF", LocalDate.now(),
                "Provende", "Fournisseur");
        FinanceDtos.RevenueResponse revenue = new FinanceDtos.RevenueResponse(UUID.randomUUID(),
                UUID.randomUUID(), "VENTES", new BigDecimal("900000"), "XOF", LocalDate.now(),
                "Ventes");

        byte[] content = SpreadsheetDocuments.financials(statement, List.of(expense),
                List.of(revenue));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("Synthèse");
            assertThat(workbook.getSheetAt(1).getSheetName()).isEqualTo("Dépenses");
            assertThat(workbook.getSheetAt(2).getSheetName()).isEqualTo("Revenus");
            assertThat(workbook.getSheetAt(1).getLastRowNum()).isEqualTo(1);
        }
    }
}
