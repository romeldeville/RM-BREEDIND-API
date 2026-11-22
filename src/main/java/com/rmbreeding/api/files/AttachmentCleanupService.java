package com.rmbreeding.api.files;

import com.rmbreeding.api.config.StorageProperties;
import com.rmbreeding.api.shared.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * P14-05 - removes uploads that were never confirmed and files deleted long ago.
 */
@Service
public class AttachmentCleanupService {

    private static final Logger LOG = LoggerFactory.getLogger(AttachmentCleanupService.class);

    private final AttachmentRepository attachmentRepository;
    private final ObjectStorage storage;
    private final StorageProperties properties;

    public AttachmentCleanupService(AttachmentRepository attachmentRepository, ObjectStorage storage,
            StorageProperties properties) {
        this.attachmentRepository = attachmentRepository;
        this.storage = storage;
        this.properties = properties;
    }

    public int cleanupAllTenants() {
        int total = 0;
        for (UUID tenantId : attachmentRepository.findAllTenantIds()) {
            TenantContext.setTenantId(tenantId);
            try {
                total += cleanupTenant();
            } catch (RuntimeException ex) {
                LOG.warn("Attachment cleanup failed for tenant {}: {}", tenantId, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        return total;
    }

    public int cleanupTenant() {
        UUID tenantId = TenantContext.requireTenantId();
        Instant now = Instant.now();
        Instant orphanCutoff = now.minus(properties.getOrphanRetention());
        Instant deletedCutoff = now.minus(properties.getDeletedRetention());

        List<AttachmentEntity> candidates = new ArrayList<>(attachmentRepository
                .findByTenantIdAndConfirmedAtIsNullAndDeletedAtIsNullAndCreatedAtBefore(tenantId,
                        orphanCutoff));
        candidates.addAll(attachmentRepository.findByTenantIdAndDeletedAtBefore(tenantId, deletedCutoff));

        Set<UUID> seen = new LinkedHashSet<>();
        int purged = 0;
        for (AttachmentEntity entity : candidates) {
            if (!seen.add(entity.id)) {
                continue;
            }
            removeObjectQuietly(entity);
            attachmentRepository.delete(entity);
            purged++;
        }
        return purged;
    }

    private void removeObjectQuietly(AttachmentEntity entity) {
        try {
            storage.removeObject(entity.bucket, entity.storageKey);
        } catch (StorageException ex) {
            LOG.debug("Orphan object {} could not be removed: {}", entity.storageKey, ex.getMessage());
        }
    }
}
