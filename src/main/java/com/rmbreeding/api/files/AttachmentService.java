package com.rmbreeding.api.files;

import com.rmbreeding.api.config.StorageProperties;
import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService {

    static final Set<String> ENTITY_TYPES = Set.of(
            "ANIMAL", "LOT", "HEALTH_EVENT", "TREATMENT", "VACCINATION",
            "MORTALITY", "EXPENSE", "REVENUE", "SALE", "CUSTOMER", "OTHER");

    private final AttachmentRepository attachmentRepository;
    private final ObjectStorage storage;
    private final StorageProperties properties;
    private final AuditService auditService;

    public AttachmentService(AttachmentRepository attachmentRepository, ObjectStorage storage,
            StorageProperties properties, AuditService auditService) {
        this.attachmentRepository = attachmentRepository;
        this.storage = storage;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Transactional
    public AttachmentDtos.PresignResponse presign(AttachmentDtos.PresignRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        String entityType = normalizeType(request.entityType());
        if (request.sizeBytes() != null && request.sizeBytes() > properties.getMaxUploadBytes()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "File exceeds the maximum upload size");
        }
        String filename = sanitizeFilename(request.filename());
        UUID id = UUID.randomUUID();
        String key = storageKey(tenantId, entityType, request.entityId(), id, filename);

        AttachmentEntity entity = new AttachmentEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.entityType = entityType;
        entity.entityId = request.entityId();
        entity.bucket = properties.getBucket();
        entity.storageKey = key;
        entity.filename = filename;
        entity.mimeType = request.mimeType();
        entity.sizeBytes = request.sizeBytes();
        entity.uploadedBy = com.rmbreeding.api.identity.AuthContext.userId();
        entity.createdAt = Instant.now();
        attachmentRepository.save(entity);

        String uploadUrl = storage.presignedPutUrl(entity.bucket, entity.storageKey,
                properties.getUploadUrlTtl());
        return new AttachmentDtos.PresignResponse(id, uploadUrl, key,
                Instant.now().plus(properties.getUploadUrlTtl()));
    }

    @Transactional
    public AttachmentDtos.AttachmentResponse confirm(UUID attachmentId, AttachmentDtos.ConfirmRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        AttachmentEntity entity = requireActive(attachmentId, tenantId);
        long size;
        try {
            size = storage.size(entity.bucket, entity.storageKey);
        } catch (StorageException ex) {
            if (ex.isNotFound()) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "The uploaded file was not found in storage");
            }
            throw ex;
        }
        entity.sizeBytes = size;
        if (request != null && request.checksum() != null && !request.checksum().isBlank()) {
            entity.checksum = request.checksum();
        }
        entity.confirmedAt = Instant.now();
        attachmentRepository.save(entity);
        auditService.record("ATTACHMENT_CONFIRMED", "ATTACHMENT", entity.id);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<AttachmentDtos.AttachmentResponse> list(String entityType, UUID entityId) {
        UUID tenantId = TenantContext.requireTenantId();
        String type = normalizeType(entityType);
        return attachmentRepository
                .findByTenantIdAndEntityTypeAndEntityIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                        tenantId, type, entityId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AttachmentDtos.AttachmentResponse get(UUID attachmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        return toResponse(requireActive(attachmentId, tenantId));
    }

    @Transactional(readOnly = true)
    public AttachmentDtos.DownloadUrlResponse downloadUrl(UUID attachmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        AttachmentEntity entity = requireActive(attachmentId, tenantId);
        if (entity.confirmedAt == null) {
            throw new BusinessRuleException(ErrorCode.CONFLICT, "The attachment upload is not confirmed");
        }
        String url = storage.presignedGetUrl(entity.bucket, entity.storageKey,
                properties.getDownloadUrlTtl());
        return new AttachmentDtos.DownloadUrlResponse(url,
                Instant.now().plus(properties.getDownloadUrlTtl()));
    }

    @Transactional
    public void delete(UUID attachmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        AttachmentEntity entity = requireActive(attachmentId, tenantId);
        entity.deletedAt = Instant.now();
        attachmentRepository.save(entity);
        try {
            storage.removeObject(entity.bucket, entity.storageKey);
        } catch (StorageException ignored) {
            // the file is purged later by the cleanup job
        }
        auditService.record("ATTACHMENT_DELETED", "ATTACHMENT", entity.id);
    }

    AttachmentEntity requireActive(UUID attachmentId, UUID tenantId) {
        AttachmentEntity entity = attachmentRepository.findByIdAndTenantId(attachmentId, tenantId)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        if (entity.deletedAt != null) {
            throw new NotFoundException("Attachment not found");
        }
        return entity;
    }

    private AttachmentDtos.AttachmentResponse toResponse(AttachmentEntity entity) {
        return new AttachmentDtos.AttachmentResponse(entity.id, entity.entityType, entity.entityId,
                entity.filename, entity.mimeType, entity.sizeBytes, entity.checksum,
                entity.confirmedAt != null, entity.createdAt, entity.confirmedAt);
    }

    static String normalizeType(String entityType) {
        String type = entityType == null ? "" : entityType.trim().toUpperCase(Locale.ROOT);
        if (!ENTITY_TYPES.contains(type)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Unsupported attachment entity type: " + entityType);
        }
        return type;
    }

    static String sanitizeFilename(String filename) {
        String value = filename == null ? "file" : filename;
        value = value.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.replaceAll("[\\p{Cntrl}]", "").trim();
        if (value.isBlank()) {
            value = "file";
        }
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }

    private static String storageKey(UUID tenantId, String entityType, UUID entityId, UUID attachmentId,
            String filename) {
        return tenantId + "/" + entityType.toLowerCase(Locale.ROOT) + "/" + entityId + "/"
                + attachmentId + extension(filename);
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,10}") ? "." + ext : "";
    }
}
