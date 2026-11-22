package com.rmbreeding.api.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

import com.rmbreeding.api.shared.TenantContext;

/**
 * P14-09 - renders a large export off the request thread.
 */
@Component
public class ExportJobWorker {

    private static final Logger LOG = LoggerFactory.getLogger(ExportJobWorker.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final ExportJobRepository exportJobRepository;
    private final ExportDocumentService documentService;
    private final ObjectStorage storage;
    private final com.rmbreeding.api.config.StorageProperties properties;

    public ExportJobWorker(ExportJobRepository exportJobRepository,
            ExportDocumentService documentService, ObjectStorage storage,
            com.rmbreeding.api.config.StorageProperties properties) {
        this.exportJobRepository = exportJobRepository;
        this.documentService = documentService;
        this.storage = storage;
        this.properties = properties;
    }

    @Async
    public void run(UUID jobId, UUID tenantId) {
        TenantContext.setTenantId(tenantId);
        try {
            ExportJobEntity job = exportJobRepository.findByIdAndTenantId(jobId, tenantId).orElse(null);
            if (job == null) {
                LOG.warn("Export job {} disappeared before processing", jobId);
                return;
            }
            job.status = ExportJobService.STATUS_RUNNING;
            job.startedAt = Instant.now();
            exportJobRepository.save(job);

            try {
                ExportDocument document = documentService.render(job.type, job.params);
                String key = tenantId + "/exports/" + jobId + "/" + document.filename();
                storage.putObject(properties.getExportBucket(), key, document.content(),
                        document.contentType());

                job.bucket = properties.getExportBucket();
                job.storageKey = key;
                job.filename = document.filename();
                job.mimeType = document.contentType();
                job.sizeBytes = (long) document.content().length;
                job.rowCount = document.rowCount();
                job.status = ExportJobService.STATUS_DONE;
                job.errorMessage = null;
                job.completedAt = Instant.now();
                exportJobRepository.save(job);
            } catch (RuntimeException ex) {
                LOG.error("Export job {} failed", jobId, ex);
                job.status = ExportJobService.STATUS_FAILED;
                job.errorMessage = truncate(ex.getMessage());
                job.completedAt = Instant.now();
                exportJobRepository.save(job);
            }
        } finally {
            TenantContext.clear();
        }
    }

    private static String truncate(String message) {
        String value = message == null || message.isBlank() ? "Export failed" : message;
        return value.length() > MAX_ERROR_LENGTH ? value.substring(0, MAX_ERROR_LENGTH) : value;
    }
}
