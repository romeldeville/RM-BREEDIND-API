package com.rmbreeding.api.files;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class PdfSupport {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    static final Font TITLE = font(Font.BOLD, 18, new Color(17, 24, 39));
    static final Font SUBTITLE = font(Font.NORMAL, 11, new Color(75, 85, 99));
    static final Font HEADING = font(Font.BOLD, 12, new Color(29, 78, 216));
    static final Font LABEL = font(Font.BOLD, 9, new Color(17, 24, 39));
    static final Font VALUE = font(Font.NORMAL, 9, new Color(31, 41, 55));
    static final Font SMALL = font(Font.NORMAL, 8, new Color(107, 114, 128));
    static final Font TOTAL = font(Font.BOLD, 10, new Color(17, 24, 39));

    private static final Color HEADER_BACKGROUND = new Color(229, 231, 235);

    private PdfSupport() {
    }

    private static Font font(int style, float size, Color color) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, size, style);
        font.setColor(color);
        return font;
    }

    static Document newDocument() {
        return new Document(PageSize.A4, 36, 36, 42, 42);
    }

    static void title(Document document, String title, String subtitle) throws DocumentException {
        Paragraph paragraph = new Paragraph(title, TITLE);
        paragraph.setSpacingAfter(4);
        document.add(paragraph);
        if (subtitle != null && !subtitle.isBlank()) {
            Paragraph sub = new Paragraph(subtitle, SUBTITLE);
            sub.setSpacingAfter(12);
            document.add(sub);
        }
    }

    static void heading(Document document, String text) throws DocumentException {
        Paragraph paragraph = new Paragraph(text, HEADING);
        paragraph.setSpacingBefore(12);
        paragraph.setSpacingAfter(6);
        document.add(paragraph);
    }

    static void keyValue(Document document, String label, Object value) throws DocumentException {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Phrase(label + " : ", LABEL));
        paragraph.add(new Phrase(format(value), VALUE));
        paragraph.setSpacingAfter(2);
        document.add(paragraph);
    }

    static PdfPTable table(String[] headers, float[] widths) {
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        try {
            table.setWidths(widths);
        } catch (DocumentException ignored) {
            // keep equal widths
        }
        table.setSpacingBefore(4);
        table.setSpacingAfter(8);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, LABEL));
            cell.setBackgroundColor(HEADER_BACKGROUND);
            cell.setPadding(5);
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(cell);
        }
        return table;
    }

    static void cell(PdfPTable table, Object value) {
        PdfPCell cell = new PdfPCell(new Phrase(format(value), VALUE));
        cell.setPadding(4);
        table.addCell(cell);
    }

    static void cell(PdfPTable table, Object value, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(format(value), VALUE));
        cell.setPadding(4);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    static void totalRow(PdfPTable table, String label, Object value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, TOTAL));
        labelCell.setColspan(Math.max(1, table.getNumberOfColumns() - 1));
        labelCell.setPadding(5);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(labelCell);
        PdfPCell valueCell = new PdfPCell(new Phrase(format(value), TOTAL));
        valueCell.setPadding(5);
        table.addCell(valueCell);
    }

    static void emptyTable(Document document, String message) throws DocumentException {
        document.add(new Paragraph(message, SUBTITLE));
    }

    static String format(Object value) {
        if (value == null) {
            return "—";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        if (value instanceof Instant instant) {
            return DATE_TIME.format(instant.atZone(ZoneId.systemDefault()));
        }
        if (value instanceof LocalDate date) {
            return DATE.format(date);
        }
        return value.toString();
    }

    static String date(Instant instant) {
        return instant == null ? "—" : DATE.format(instant.atZone(ZoneId.systemDefault()));
    }
}
