package com.rmbreeding.api.files;

import java.util.Map;

public record ExportDocument(byte[] content, String filename, String contentType, int rowCount) {

    public static ExportDocument pdf(String filename, byte[] content, int rowCount) {
        return new ExportDocument(content, filename, "application/pdf", rowCount);
    }

    public static ExportDocument xlsx(String filename, byte[] content, int rowCount) {
        return new ExportDocument(content, filename,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", rowCount);
    }

    public static Map<String, Object> emptyParams() {
        return Map.of();
    }
}
