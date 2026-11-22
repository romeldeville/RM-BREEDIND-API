package com.rmbreeding.api.files;

import com.rmbreeding.api.config.StorageProperties;
import com.rmbreeding.api.identity.AuthContext;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ShareService {

    private final ShareLinkRepository shareLinkRepository;
    private final AttachmentService attachmentService;
    private final ExportJobRepository exportJobRepository;
    private final ObjectStorage storage;
    private final StorageProperties properties;
    private final ShareTokenService tokenService;

    public ShareService(ShareLinkRepository shareLinkRepository, AttachmentService attachmentService,
            ExportJobRepository exportJobRepository, ObjectStorage storage,
            StorageProperties properties, ShareTokenService tokenService) {
        this.shareLinkRepository = shareLinkRepository;
        this.attachmentService = attachmentService;
        this.exportJobRepository = exportJobRepository;
        this.storage = storage;
        this.properties = properties;
        this.tokenService = tokenService;
    }

    @Transactional
    public ShareDtos.ShareLinkResponse create(ShareDtos.ShareCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        Source source = resolveSource(request, tenantId);

        Duration ttl = request.ttlHours() == null
                ? properties.getShareTtl()
                : Duration.ofHours(request.ttlHours());
        if (ttl.compareTo(properties.getShareMaxTtl()) > 0) {
            ttl = properties.getShareMaxTtl();
        }

        Instant now = Instant.now();
        ShareLinkEntity entity = new ShareLinkEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.attachmentId = source.attachmentId;
        entity.bucket = source.bucket;
        entity.storageKey = source.storageKey;
        entity.filename = source.filename;
        entity.mimeType = source.mimeType;
        entity.sizeBytes = source.sizeBytes;
        entity.entityType = source.entityType;
        entity.entityId = source.entityId;
        entity.createdBy = AuthContext.userId();
        entity.expiresAt = now.plus(ttl);
        entity.createdAt = now;
        shareLinkRepository.save(entity);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<ShareDtos.ShareLinkResponse> list(String entityType, UUID entityId) {
        UUID tenantId = TenantContext.requireTenantId();
        List<ShareLinkEntity> links;
        if (entityType != null && !entityType.isBlank() && entityId != null) {
            links = shareLinkRepository
                    .findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(tenantId,
                            AttachmentService.normalizeType(entityType), entityId);
        } else {
            links = shareLinkRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        return links.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ShareDtos.ShareLinkResponse revoke(UUID shareId) {
        UUID tenantId = TenantContext.requireTenantId();
        ShareLinkEntity entity = shareLinkRepository.findByIdAndTenantId(shareId, tenantId)
                .orElseThrow(() -> new NotFoundException("Share link not found"));
        if (entity.revokedAt == null) {
            entity.revokedAt = Instant.now();
            shareLinkRepository.save(entity);
        }
        return toResponse(entity);
    }

    @Transactional
    public void delete(UUID shareId) {
        UUID tenantId = TenantContext.requireTenantId();
        ShareLinkEntity entity = shareLinkRepository.findByIdAndTenantId(shareId, tenantId)
                .orElseThrow(() -> new NotFoundException("Share link not found"));
        shareLinkRepository.delete(entity);
    }

    public PublicShareFile open(String token) {
        ShareTokenService.ShareTokenPayload payload = tokenService.verify(token);
        UUID previous = TenantContext.getTenantId();
        TenantContext.setTenantId(payload.tenantId());
        try {
            ShareLinkEntity entity = shareLinkRepository
                    .findByIdAndTenantId(payload.linkId(), payload.tenantId())
                    .orElseThrow(() -> new NotFoundException("Share link not found"));
            if (entity.revokedAt != null) {
                throw new BusinessRuleException(ErrorCode.EXPIRED, "Share link has been revoked");
            }
            if (entity.expiresAt.isBefore(Instant.now())) {
                throw new BusinessRuleException(ErrorCode.EXPIRED, "Share link has expired");
            }
            byte[] content = storage.getObject(entity.bucket, entity.storageKey);
            entity.accessCount = entity.accessCount + 1;
            entity.lastAccessedAt = Instant.now();
            shareLinkRepository.save(entity);
            return new PublicShareFile(entity.filename, entity.mimeType, content);
        } catch (StorageException ex) {
            if (ex.isNotFound()) {
                throw new NotFoundException("Shared file not found");
            }
            throw ex;
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previous);
            }
        }
    }

    private Source resolveSource(ShareDtos.ShareCreateRequest request, UUID tenantId) {
        if (request.attachmentId() != null) {
            AttachmentEntity attachment = attachmentService.requireActive(request.attachmentId(), tenantId);
            if (attachment.confirmedAt == null) {
                throw new ConflictException("The attachment upload is not confirmed");
            }
            return new Source(attachment.id, attachment.bucket, attachment.storageKey,
                    attachment.filename, attachment.mimeType, attachment.sizeBytes,
                    attachment.entityType, attachment.entityId);
        }
        if (request.exportJobId() != null) {
            ExportJobEntity job = exportJobRepository
                    .findByIdAndTenantId(request.exportJobId(), tenantId)
                    .orElseThrow(() -> new NotFoundException("Export job not found"));
            if (!ExportJobService.STATUS_DONE.equals(job.status) || job.storageKey == null) {
                throw new ConflictException("The export job is not ready");
            }
            String entityType = request.entityType() == null ? null
                    : AttachmentService.normalizeType(request.entityType());
            return new Source(null, job.bucket, job.storageKey, job.filename, job.mimeType,
                    job.sizeBytes, entityType, request.entityId());
        }
        throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                "attachmentId or exportJobId is required");
    }

    private ShareDtos.ShareLinkResponse toResponse(ShareLinkEntity entity) {
        String token = tokenService.issue(entity.tenantId, entity.id, entity.expiresAt);
        String base = properties.getPublicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = properties.getSharePath().startsWith("/")
                ? properties.getSharePath()
                : "/" + properties.getSharePath();
        String url = base + path + "/" + token;
        return new ShareDtos.ShareLinkResponse(entity.id, url, entity.filename, entity.mimeType,
                entity.sizeBytes, entity.entityType, entity.entityId, entity.expiresAt,
                entity.revokedAt, entity.accessCount, entity.lastAccessedAt, entity.createdAt);
    }

    private record Source(UUID attachmentId, String bucket, String storageKey, String filename,
            String mimeType, Long sizeBytes, String entityType, UUID entityId) {
    }

    public record PublicShareFile(String filename, String mimeType, byte[] content) {
    }
}
