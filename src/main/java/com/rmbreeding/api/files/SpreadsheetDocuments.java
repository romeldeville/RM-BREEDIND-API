package com.rmbreeding.api.files;

import com.rmbreeding.api.finance.FinanceDtos;
import com.rmbreeding.api.herd.HerdDtos;
import com.rmbreeding.api.herd.LotDtos;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

final class SpreadsheetDocuments {

    private SpreadsheetDocuments() {
    }

    static byte[] financials(FinanceDtos.FinancialStatementResponse statement,
            List<FinanceDtos.ExpenseResponse> expenses,
            List<FinanceDtos.RevenueResponse> revenues) {
        return write(workbook -> {
            CellStyle header = headerStyle(workbook);

            Sheet summary = workbook.createSheet("Synthèse");
            Row title = summary.createRow(0);
            title.createCell(0).setCellValue("Période");
            title.createCell(1).setCellValue(PdfSupport.format(statement.from()) + " → "
                    + PdfSupport.format(statement.to()));
            Row revenue = summary.createRow(1);
            revenue.createCell(0).setCellValue("Total revenus");
            revenue.createCell(1).setCellValue(number(statement.totalRevenue()));
            Row expense = summary.createRow(2);
            expense.createCell(0).setCellValue("Total dépenses");
            expense.createCell(1).setCellValue(number(statement.totalExpense()));
            Row net = summary.createRow(3);
            net.createCell(0).setCellValue("Résultat net");
            net.createCell(1).setCellValue(number(statement.net()));

            int rowIndex = 5;
            rowIndex = lineSection(summary, rowIndex, "Revenus par catégorie", statement.revenues(), header);
            rowIndex = lineSection(summary, rowIndex, "Dépenses par catégorie", statement.expenses(), header);
            summary.setColumnWidth(0, 32 * 256);
            summary.setColumnWidth(1, 18 * 256);

            Sheet expenseSheet = workbook.createSheet("Dépenses");
            Row expenseHeader = expenseSheet.createRow(0);
            writeHeader(expenseHeader, header, "Date", "Catégorie", "Montant", "Devise",
                    "Fournisseur", "Description");
            int index = 1;
            for (FinanceDtos.ExpenseResponse item : expenses) {
                Row row = expenseSheet.createRow(index++);
                setDate(row, 0, item.incurredAt());
                setText(row, 1, item.category());
                setNumber(row, 2, item.amount());
                setText(row, 3, item.currency());
                setText(row, 4, item.supplier());
                setText(row, 5, item.description());
            }
            widths(expenseSheet, 12, 20, 14, 8, 24, 40);

            Sheet revenueSheet = workbook.createSheet("Revenus");
            Row revenueHeader = revenueSheet.createRow(0);
            writeHeader(revenueHeader, header, "Date", "Catégorie", "Montant", "Devise", "Description");
            index = 1;
            for (FinanceDtos.RevenueResponse item : revenues) {
                Row row = revenueSheet.createRow(index++);
                setDate(row, 0, item.receivedAt());
                setText(row, 1, item.category());
                setNumber(row, 2, item.amount());
                setText(row, 3, item.currency());
                setText(row, 4, item.description());
            }
            widths(revenueSheet, 12, 20, 14, 8, 40);
        });
    }

    static byte[] animals(List<HerdDtos.AnimalResponse> animals) {
        return write(workbook -> {
            CellStyle header = headerStyle(workbook);
            Sheet sheet = workbook.createSheet("Animaux");
            writeHeader(sheet.createRow(0), header, "Identifiant", "Nom", "Sexe",
                    "Date de naissance", "Statut", "Origine", "Poids (kg)", "Mère", "Père",
                    "Créé le");
            int index = 1;
            for (HerdDtos.AnimalResponse animal : animals) {
                Row row = sheet.createRow(index++);
                setText(row, 0, animal.identifier());
                setText(row, 1, animal.name());
                setText(row, 2, animal.sex());
                setDate(row, 3, animal.birthDate());
                setText(row, 4, animal.status());
                setText(row, 5, animal.origin());
                setNumber(row, 6, animal.weightKg());
                setText(row, 7, animal.motherId());
                setText(row, 8, animal.fatherId());
                setInstant(row, 9, animal.createdAt());
            }
            widths(sheet, 18, 20, 8, 16, 14, 16, 12, 36, 36, 20);
        });
    }

    static byte[] lots(List<LotDtos.LotResponse> lots) {
        return write(workbook -> {
            CellStyle header = headerStyle(workbook);
            Sheet sheet = workbook.createSheet("Lots");
            writeHeader(sheet.createRow(0), header, "Nom", "Code", "Production", "Statut",
                    "Entrée", "Effectif initial", "Effectif actuel", "Coût unitaire", "Clôturé le");
            int index = 1;
            for (LotDtos.LotResponse lot : lots) {
                Row row = sheet.createRow(index++);
                setText(row, 0, lot.name());
                setText(row, 1, lot.code());
                setText(row, 2, lot.productionType());
                setText(row, 3, lot.status());
                setDate(row, 4, lot.entryDate());
                setInteger(row, 5, lot.initialCount());
                setInteger(row, 6, lot.currentCount());
                setNumber(row, 7, lot.unitCost());
                setInstant(row, 8, lot.closedAt());
            }
            widths(sheet, 24, 14, 18, 12, 14, 16, 16, 14, 20);
        });
    }

    private static int lineSection(Sheet sheet, int startRow, String title,
            List<FinanceDtos.FinancialStatementLine> lines, CellStyle header) {
        Row titleRow = sheet.createRow(startRow);
        titleRow.createCell(0).setCellValue(title);
        titleRow.getCell(0).setCellStyle(header);
        int index = startRow + 1;
        for (FinanceDtos.FinancialStatementLine line : lines) {
            Row row = sheet.createRow(index++);
            setText(row, 0, line.category());
            setNumber(row, 1, line.amount());
        }
        return index + 1;
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static void writeHeader(Row row, CellStyle style, String... labels) {
        for (int i = 0; i < labels.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(labels[i]);
            cell.setCellStyle(style);
        }
    }

    private static void widths(Sheet sheet, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private static void setText(Row row, int column, Object value) {
        row.createCell(column).setCellValue(value == null ? "" : value.toString());
    }

    private static void setNumber(Row row, int column, BigDecimal value) {
        if (value == null) {
            row.createCell(column).setCellValue("");
        } else {
            row.createCell(column).setCellValue(value.doubleValue());
        }
    }

    private static void setInteger(Row row, int column, Integer value) {
        if (value == null) {
            row.createCell(column).setCellValue("");
        } else {
            row.createCell(column).setCellValue(value.doubleValue());
        }
    }

    private static void setDate(Row row, int column, LocalDate value) {
        row.createCell(column).setCellValue(value == null ? "" : PdfSupport.format(value));
    }

    private static void setInstant(Row row, int column, Instant value) {
        row.createCell(column).setCellValue(value == null ? ""
                : PdfSupport.DATE.format(value.atZone(ZoneId.systemDefault())));
    }

    private static double number(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }

    private interface WorkbookWriter {
        void write(XSSFWorkbook workbook) throws IOException;
    }

    private static byte[] write(WorkbookWriter writer) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writer.write(workbook);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate spreadsheet", ex);
        }
    }
}
