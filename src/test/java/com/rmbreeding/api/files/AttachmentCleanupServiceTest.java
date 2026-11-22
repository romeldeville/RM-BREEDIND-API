package com.rmbreeding.api.files;

import com.rmbreeding.api.config.StorageProperties;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentCleanupServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private ObjectStorage storage;

    private final StorageProperties properties = new StorageProperties();
    private AttachmentCleanupService service;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties.setOrphanRetention(Duration.ofHours(24));
        properties.setDeletedRetention(Duration.ofDays(7));
        service = new AttachmentCleanupService(attachmentRepository, storage, properties);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void purgesUnconfirmedAndOldDeletedAttachmentsOnce() {
        AttachmentEntity orphan = attachment("orphan.jpg");
        AttachmentEntity deleted = attachment("deleted.jpg");
        when(attachmentRepository.findByTenantIdAndConfirmedAtIsNullAndDeletedAtIsNullAndCreatedAtBefore(
                eq(tenantId), any(Instant.class))).thenReturn(List.of(orphan));
        when(attachmentRepository.findByTenantIdAndDeletedAtBefore(eq(tenantId), any(Instant.class)))
                .thenReturn(List.of(deleted, orphan));

        int purged = service.cleanupTenant();

        org.assertj.core.api.Assertions.assertThat(purged).isEqualTo(2);
        verify(storage).removeObject(orphan.bucket, orphan.storageKey);
        verify(storage).removeObject(deleted.bucket, deleted.storageKey);
        verify(attachmentRepository).delete(orphan);
        verify(attachmentRepository).delete(deleted);
    }

    @Test
    void keepsRecentAttachments() {
        when(attachmentRepository.findByTenantIdAndConfirmedAtIsNullAndDeletedAtIsNullAndCreatedAtBefore(
                eq(tenantId), any(Instant.class))).thenReturn(List.of());
        when(attachmentRepository.findByTenantIdAndDeletedAtBefore(eq(tenantId), any(Instant.class)))
                .thenReturn(List.of());

        int purged = service.cleanupTenant();

        org.assertj.core.api.Assertions.assertThat(purged).isZero();
        verify(attachmentRepository, never()).delete(any(AttachmentEntity.class));
    }

    private AttachmentEntity attachment(String filename) {
        AttachmentEntity entity = new AttachmentEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.bucket = "rm-breeding";
        entity.storageKey = tenantId + "/animal/" + entity.id + "/" + filename;
        entity.filename = filename;
        entity.createdAt = Instant.now().minus(Duration.ofDays(9));
        return entity;
    }
}
