package com.rmbreeding.api.files;

import com.rmbreeding.api.config.StorageProperties;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportJobServiceTest {

    @Mock
    private ExportJobRepository exportJobRepository;
    @Mock
    private ExportJobWorker worker;
    @Mock
    private ObjectStorage storage;

    private final StorageProperties properties = new StorageProperties();
    private ExportJobService service;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties.setExportBucket("rm-breeding-exports");
        service = new ExportJobService(exportJobRepository, worker, storage, properties);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createStoresPendingJobAndStartsWorker() {
        when(exportJobRepository.save(any(ExportJobEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExportJobDtos.ExportJobResponse response = service.create(
                new ExportJobDtos.ExportJobCreateRequest("animals_xlsx", Map.of("status", "ACTIVE")));

        assertThat(response.status()).isEqualTo(ExportJobService.STATUS_PENDING);
        assertThat(response.type()).isEqualTo(ExportDocumentService.ANIMALS_XLSX);
        assertThat(response.params()).containsEntry("status", "ACTIVE");
        assertThat(response.downloadUrl()).isNull();
        verify(worker).run(response.id(), tenantId);
    }

    @Test
    void createRejectsUnknownType() {
        assertThatThrownBy(() -> service.create(
                new ExportJobDtos.ExportJobCreateRequest("NOT_A_TYPE", Map.of())))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void downloadUrlRequiresCompletedJob() {
        ExportJobEntity job = job(ExportJobService.STATUS_RUNNING);
        when(exportJobRepository.findByIdAndTenantId(job.id, tenantId))
                .thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.downloadUrl(job.id))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void downloadUrlSignsStoredExport() {
        ExportJobEntity job = job(ExportJobService.STATUS_DONE);
        job.bucket = "rm-breeding-exports";
        job.storageKey = tenantId + "/exports/" + job.id + "/animaux.xlsx";
        when(exportJobRepository.findByIdAndTenantId(job.id, tenantId))
                .thenReturn(Optional.of(job));
        when(storage.presignedGetUrl(eq(job.bucket), eq(job.storageKey), any()))
                .thenReturn("http://minio/export");

        ExportJobDtos.ExportDownloadResponse response = service.downloadUrl(job.id);

        assertThat(response.url()).isEqualTo("http://minio/export");
        assertThat(response.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void getReturnsNotFoundForAnotherTenant() {
        UUID jobId = UUID.randomUUID();
        when(exportJobRepository.findByIdAndTenantId(jobId, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(jobId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listRejectsUnknownStatus() {
        assertThatThrownBy(() -> service.list("BROKEN"))
                .isInstanceOf(BusinessRuleException.class);
    }

    private ExportJobEntity job(String status) {
        ExportJobEntity job = new ExportJobEntity();
        job.id = UUID.randomUUID();
        job.tenantId = tenantId;
        job.type = ExportDocumentService.ANIMALS_XLSX;
        job.status = status;
        job.params = new HashMap<>();
        job.createdAt = Instant.now();
        return job;
    }
}
