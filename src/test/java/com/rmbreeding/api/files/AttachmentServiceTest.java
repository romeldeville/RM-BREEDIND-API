package com.rmbreeding.api.files;

import com.rmbreeding.api.config.StorageProperties;
import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private ObjectStorage storage;
    @Mock
    private AuditService auditService;

    private final StorageProperties properties = new StorageProperties();
    private AttachmentService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID entityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties.setBucket("rm-breeding");
        properties.setUploadUrlTtl(Duration.ofMinutes(10));
        properties.setDownloadUrlTtl(Duration.ofMinutes(5));
        service = new AttachmentService(attachmentRepository, storage, properties, auditService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void presignCreatesPendingAttachmentAndSignedUrl() {
        when(attachmentRepository.save(any(AttachmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.presignedPutUrl(eq("rm-breeding"), anyString(), any()))
                .thenReturn("http://minio/upload");

        AttachmentDtos.PresignResponse response = service.presign(new AttachmentDtos.PresignRequest(
                "animal", entityId, "../photos/evil.jpg", "image/jpeg", 1024L));

        assertThat(response.attachmentId()).isNotNull();
        assertThat(response.uploadUrl()).isEqualTo("http://minio/upload");
        assertThat(response.storageKey())
                .isEqualTo(tenantId + "/animal/" + entityId + "/" + response.attachmentId() + ".jpg");

        ArgumentCaptor<AttachmentEntity> captor = ArgumentCaptor.forClass(AttachmentEntity.class);
        verify(attachmentRepository).save(captor.capture());
        AttachmentEntity saved = captor.getValue();
        assertThat(saved.tenantId).isEqualTo(tenantId);
        assertThat(saved.entityType).isEqualTo("ANIMAL");
        assertThat(saved.filename).isEqualTo("evil.jpg");
        assertThat(saved.confirmedAt).isNull();
        assertThat(saved.sizeBytes).isEqualTo(1024L);
    }

    @Test
    void presignRejectsUnsupportedEntityType() {
        assertThatThrownBy(() -> service.presign(new AttachmentDtos.PresignRequest("UNKNOWN",
                entityId, "file.pdf", "application/pdf", 10L)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void presignRejectsTooLargeFile() {
        properties.setMaxUploadBytes(1000);

        assertThatThrownBy(() -> service.presign(new AttachmentDtos.PresignRequest("ANIMAL",
                entityId, "file.pdf", "application/pdf", 1001L)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void confirmStoresSizeAndChecksum() {
        AttachmentEntity entity = attachment(Instant.now());
        entity.confirmedAt = null;
        when(attachmentRepository.findByIdAndTenantId(entity.id, tenantId))
                .thenReturn(Optional.of(entity));
        when(storage.size(entity.bucket, entity.storageKey)).thenReturn(2048L);
        when(attachmentRepository.save(entity)).thenReturn(entity);

        AttachmentDtos.AttachmentResponse response = service.confirm(entity.id,
                new AttachmentDtos.ConfirmRequest("abc123"));

        assertThat(response.confirmed()).isTrue();
        assertThat(response.sizeBytes()).isEqualTo(2048L);
        assertThat(response.checksum()).isEqualTo("abc123");
        verify(auditService).record("ATTACHMENT_CONFIRMED", "ATTACHMENT", entity.id);
    }

    @Test
    void confirmFailsWhenObjectIsMissing() {
        AttachmentEntity entity = attachment(Instant.now());
        entity.confirmedAt = null;
        when(attachmentRepository.findByIdAndTenantId(entity.id, tenantId))
                .thenReturn(Optional.of(entity));
        when(storage.size(entity.bucket, entity.storageKey))
                .thenThrow(StorageException.notFound("missing"));

        assertThatThrownBy(() -> service.confirm(entity.id, null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void downloadUrlRequiresConfirmedUpload() {
        AttachmentEntity entity = attachment(Instant.now());
        entity.confirmedAt = null;
        when(attachmentRepository.findByIdAndTenantId(entity.id, tenantId))
                .thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.downloadUrl(entity.id))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void downloadUrlOfConfirmedAttachmentIsSigned() {
        AttachmentEntity entity = attachment(Instant.now());
        when(attachmentRepository.findByIdAndTenantId(entity.id, tenantId))
                .thenReturn(Optional.of(entity));
        when(storage.presignedGetUrl(entity.bucket, entity.storageKey, properties.getDownloadUrlTtl()))
                .thenReturn("http://minio/download");

        AttachmentDtos.DownloadUrlResponse response = service.downloadUrl(entity.id);

        assertThat(response.url()).isEqualTo("http://minio/download");
        assertThat(response.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void deleteSoftDeletesAndRemovesObject() {
        AttachmentEntity entity = attachment(Instant.now());
        when(attachmentRepository.findByIdAndTenantId(entity.id, tenantId))
                .thenReturn(Optional.of(entity));
        when(attachmentRepository.save(entity)).thenReturn(entity);

        service.delete(entity.id);

        assertThat(entity.deletedAt).isNotNull();
        verify(storage).removeObject(entity.bucket, entity.storageKey);
        verify(auditService).record("ATTACHMENT_DELETED", "ATTACHMENT", entity.id);
    }

    @Test
    void deletedAttachmentIsNotFound() {
        AttachmentEntity entity = attachment(Instant.now());
        entity.deletedAt = Instant.now();
        when(attachmentRepository.findByIdAndTenantId(entity.id, tenantId))
                .thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.get(entity.id)).isInstanceOf(NotFoundException.class);
    }

    private AttachmentEntity attachment(Instant createdAt) {
        AttachmentEntity entity = new AttachmentEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.entityType = "ANIMAL";
        entity.entityId = entityId;
        entity.bucket = "rm-breeding";
        entity.storageKey = tenantId + "/animal/" + entityId + "/" + entity.id + ".jpg";
        entity.filename = "photo.jpg";
        entity.mimeType = "image/jpeg";
        entity.createdAt = createdAt;
        entity.confirmedAt = createdAt;
        return entity;
    }
}
