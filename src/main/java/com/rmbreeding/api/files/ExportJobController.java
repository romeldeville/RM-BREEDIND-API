package com.rmbreeding.api.files;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/export-jobs")
public class ExportJobController {

    private final ExportJobService exportJobService;

    public ExportJobController(ExportJobService exportJobService) {
        this.exportJobService = exportJobService;
    }

    @PostMapping
    public ResponseEntity<ExportJobDtos.ExportJobResponse> create(
            @Valid @RequestBody ExportJobDtos.ExportJobCreateRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(exportJobService.create(request));
    }

    @GetMapping
    public List<ExportJobDtos.ExportJobResponse> list(
            @RequestParam(required = false) String status) {
        return exportJobService.list(status);
    }

    @GetMapping("/{jobId}")
    public ExportJobDtos.ExportJobResponse get(@PathVariable UUID jobId) {
        return exportJobService.get(jobId);
    }

    @GetMapping("/{jobId}/download")
    public ExportJobDtos.ExportDownloadResponse download(@PathVariable UUID jobId) {
        return exportJobService.downloadUrl(jobId);
    }
}
