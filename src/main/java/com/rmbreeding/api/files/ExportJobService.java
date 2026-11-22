package com.rmbreeding.api.files;

import com.rmbreeding.api.config.StorageProperties;
import com.rmbreeding.api.identity.AuthContext;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ExportJobService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";

    private static final Set<String> STATUSES = Set.of(STATUS_PENDING, STATUS_RUNNING,
            STATUS_DONE, STATUS_FAILED);

    private final ExportJobRepository exportJobRepository;
    private final ExportJobWorker worker;
    private final ObjectStorage storage;
    private final StorageProperties properties;

    public ExportJobService(ExportJobRepository exportJobRepository, ExportJobWorker worker,
            ObjectStorage storage, StorageProperties properties) {
        this.exportJobRepository = exportJobRepository;
        this.worker = worker;
        this.storage = storage;
        this.properties = properties;
    }

    public ExportJobDtos.ExportJobResponse create(ExportJobDtos.ExportJobCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        String type = ExportDocumentService.normalizeType(request.type());

        ExportJobEntity entity = new ExportJobEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.type = type;
        entity.status = STATUS_PENDING;
        entity.params = request.params() == null ? new HashMap<>() : new HashMap<>(request.params());
        entity.requestedBy = AuthContext.userId();
        entity.createdAt = Instant.now();
        exportJobRepository.save(entity);

        worker.run(entity.id, tenantId);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<ExportJobDtos.ExportJobResponse> list(String status) {
        UUID tenantId = TenantContext.requireTenantId();
        List<ExportJobEntity> jobs;
        if (status == null || status.isBlank()) {
            jobs = exportJobRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        } else {
            String normalized = status.trim().toUpperCase(java.util.Locale.ROOT);
            if (!STATUSES.contains(normalized)) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Unsupported export job status: " + status);
            }
            jobs = exportJobRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId,
                    normalized);
        }
        return jobs.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ExportJobDtos.ExportJobResponse get(UUID jobId) {
        UUID tenantId = TenantContext.requireTenantId();
        return toResponse(requireJob(jobId, tenantId));
    }

    @Transactional(readOnly = true)
    public ExportJobDtos.ExportDownloadResponse downloadUrl(UUID jobId) {
        UUID tenantId = TenantContext.requireTenantId();
        ExportJobEntity job = requireJob(jobId, tenantId);
        if (!STATUS_DONE.equals(job.status) || job.storageKey == null) {
            throw new BusinessRuleException(ErrorCode.CONFLICT, "The export job is not ready");
        }
        String url = storage.presignedGetUrl(job.bucket, job.storageKey,
                properties.getExportUrlTtl());
        return new ExportJobDtos.ExportDownloadResponse(url,
                Instant.now().plus(properties.getExportUrlTtl()));
    }

    private ExportJobEntity requireJob(UUID jobId, UUID tenantId) {
        return exportJobRepository.findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new NotFoundException("Export job not found"));
    }

    private ExportJobDtos.ExportJobResponse toResponse(ExportJobEntity entity) {
        String downloadUrl = null;
        if (STATUS_DONE.equals(entity.status) && entity.storageKey != null) {
            downloadUrl = storage.presignedGetUrl(entity.bucket, entity.storageKey,
                    properties.getExportUrlTtl());
        }
        Map<String, Object> params = entity.params == null ? Map.of() : Map.copyOf(entity.params);
        return new ExportJobDtos.ExportJobResponse(entity.id, entity.type, entity.status, params,
                entity.filename, entity.mimeType, entity.sizeBytes, entity.rowCount,
                entity.errorMessage, downloadUrl, entity.createdAt, entity.startedAt,
                entity.completedAt);
    }
}
