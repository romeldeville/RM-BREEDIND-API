package com.rmbreeding.api.files;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * P14-06..P14-08 - synchronous PDF and Excel exports.
 */
@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

    private final ExportDocumentService documentService;

    public ExportController(ExportDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/animals/{animalId}/sheet")
    public ResponseEntity<byte[]> animalSheet(@PathVariable UUID animalId) {
        return download(documentService.render(ExportDocumentService.ANIMAL_SHEET,
                params("animalId", animalId)));
    }

    @GetMapping("/animals/{animalId}/health-booklet")
    public ResponseEntity<byte[]> healthBooklet(@PathVariable UUID animalId) {
        return download(documentService.render(ExportDocumentService.HEALTH_BOOKLET,
                params("animalId", animalId)));
    }

    @GetMapping("/sales/{saleId}/receipt")
    public ResponseEntity<byte[]> saleReceipt(@PathVariable UUID saleId) {
        return download(documentService.render(ExportDocumentService.SALE_RECEIPT,
                params("saleId", saleId)));
    }

    @GetMapping("/sales/register")
    public ResponseEntity<byte[]> salesRegister(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return download(documentService.render(ExportDocumentService.SALES_REGISTER,
                params("from", from, "to", to)));
    }

    @GetMapping("/lots/{lotId}/balance")
    public ResponseEntity<byte[]> lotBalance(@PathVariable UUID lotId) {
        return download(documentService.render(ExportDocumentService.LOT_BALANCE,
                params("lotId", lotId)));
    }

    @GetMapping("/finances.xlsx")
    public ResponseEntity<byte[]> finances(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return download(documentService.render(ExportDocumentService.FINANCIALS_XLSX,
                params("from", from, "to", to)));
    }

    @GetMapping("/animals.xlsx")
    public ResponseEntity<byte[]> animals(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        return download(documentService.render(ExportDocumentService.ANIMALS_XLSX,
                params("search", search, "status", status)));
    }

    @GetMapping("/lots.xlsx")
    public ResponseEntity<byte[]> lots(@RequestParam(required = false) String status) {
        return download(documentService.render(ExportDocumentService.LOTS_XLSX,
                params("status", status)));
    }

    private static ResponseEntity<byte[]> download(ExportDocument document) {
        String encoded = URLEncoder.encode(document.filename(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, document.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentLength(document.content().length)
                .body(document.content());
    }

    private static Map<String, Object> params(Object... values) {
        Map<String, Object> params = new HashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            if (values[index + 1] != null) {
                params.put((String) values[index], values[index + 1]);
            }
        }
        return params;
    }
}
