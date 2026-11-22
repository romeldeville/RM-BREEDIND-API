package com.rmbreeding.api.files;

import com.rmbreeding.api.config.StorageProperties;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ConflictException;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private ShareLinkRepository shareLinkRepository;
    @Mock
    private AttachmentService attachmentService;
    @Mock
    private ExportJobRepository exportJobRepository;
    @Mock
    private ObjectStorage storage;

    private final StorageProperties properties = new StorageProperties();
    private ShareTokenService tokenService;
    private ShareService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID attachmentId = UUID.randomUUID();
    private final UUID animalId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties.setShareSecret("unit-test-share-secret-0123456789abcdef");
        properties.setPublicBaseUrl("http://localhost:8081");
        tokenService = new ShareTokenService(properties);
        service = new ShareService(shareLinkRepository, attachmentService, exportJobRepository,
                storage, properties, tokenService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createsShareLinkForConfirmedAttachment() {
        when(attachmentService.requireActive(attachmentId, tenantId))
                .thenReturn(attachment());
        when(shareLinkRepository.save(any(ShareLinkEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ShareDtos.ShareLinkResponse response = service.create(new ShareDtos.ShareCreateRequest(
                attachmentId, null, null, null, 24));

        assertThat(response.url()).startsWith("http://localhost:8081/api/v1/public/shares/");
        assertThat(response.filename()).isEqualTo("photo.jpg");
        assertThat(response.entityType()).isEqualTo("ANIMAL");
        assertThat(response.entityId()).isEqualTo(animalId);
        assertThat(response.expiresAt()).isAfter(Instant.now().plusSeconds(23 * 3600));

        ShareTokenService.ShareTokenPayload payload = tokenService.verify(
                response.url().substring(response.url().lastIndexOf('/') + 1));
        assertThat(payload.tenantId()).isEqualTo(tenantId);
        assertThat(payload.linkId()).isEqualTo(response.id());
    }

    @Test
    void rejectsUnconfirmedAttachment() {
        AttachmentEntity attachment = attachment();
        attachment.confirmedAt = null;
        when(attachmentService.requireActive(attachmentId, tenantId)).thenReturn(attachment);

        assertThatThrownBy(() -> service.create(new ShareDtos.ShareCreateRequest(attachmentId,
                null, null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsExportJobThatIsNotReady() {
        UUID jobId = UUID.randomUUID();
        ExportJobEntity job = new ExportJobEntity();
        job.id = jobId;
        job.tenantId = tenantId;
        job.status = ExportJobService.STATUS_RUNNING;
        when(exportJobRepository.findByIdAndTenantId(jobId, tenantId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.create(new ShareDtos.ShareCreateRequest(null, jobId,
                null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void requiresAttachmentOrExportJob() {
        assertThatThrownBy(() -> service.create(new ShareDtos.ShareCreateRequest(null, null,
                null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void opensSharedFileAndCountsAccess() {
        ShareLinkEntity link = link();
        String token = tokenService.issue(tenantId, link.id, link.expiresAt);
        when(shareLinkRepository.findByIdAndTenantId(link.id, tenantId))
                .thenReturn(Optional.of(link));
        when(storage.getObject(link.bucket, link.storageKey)).thenReturn("contenu".getBytes());
        when(shareLinkRepository.save(link)).thenReturn(link);

        ShareService.PublicShareFile file = service.open(token);

        assertThat(file.filename()).isEqualTo("photo.jpg");
        assertThat(file.content()).isEqualTo("contenu".getBytes());
        assertThat(link.accessCount).isEqualTo(1);
        assertThat(link.lastAccessedAt).isNotNull();
        assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void refusesRevokedLink() {
        ShareLinkEntity link = link();
        link.revokedAt = Instant.now();
        String token = tokenService.issue(tenantId, link.id, link.expiresAt);
        when(shareLinkRepository.findByIdAndTenantId(link.id, tenantId))
                .thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.open(token))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getCode())
                        .isEqualTo(ErrorCode.EXPIRED));
    }

    @Test
    void refusesTamperedToken() {
        String token = tokenService.issue(tenantId, UUID.randomUUID(),
                Instant.now().plusSeconds(3600));

        assertThatThrownBy(() -> service.open(token + "x"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void revokeMarksLink() {
        ShareLinkEntity link = link();
        when(shareLinkRepository.findByIdAndTenantId(link.id, tenantId))
                .thenReturn(Optional.of(link));
        when(shareLinkRepository.save(link)).thenReturn(link);

        ShareDtos.ShareLinkResponse response = service.revoke(link.id);

        assertThat(response.revokedAt()).isNotNull();
        verify(shareLinkRepository).save(link);
    }

    private AttachmentEntity attachment() {
        AttachmentEntity entity = new AttachmentEntity();
        entity.id = attachmentId;
        entity.tenantId = tenantId;
        entity.entityType = "ANIMAL";
        entity.entityId = animalId;
        entity.bucket = "rm-breeding";
        entity.storageKey = tenantId + "/animal/" + animalId + "/" + attachmentId + ".jpg";
        entity.filename = "photo.jpg";
        entity.mimeType = "image/jpeg";
        entity.sizeBytes = 1024L;
        entity.createdAt = Instant.now();
        entity.confirmedAt = Instant.now();
        return entity;
    }

    private ShareLinkEntity link() {
        ShareLinkEntity link = new ShareLinkEntity();
        link.id = UUID.randomUUID();
        link.tenantId = tenantId;
        link.attachmentId = attachmentId;
        link.bucket = "rm-breeding";
        link.storageKey = tenantId + "/animal/" + animalId + "/" + attachmentId + ".jpg";
        link.filename = "photo.jpg";
        link.mimeType = "image/jpeg";
        link.sizeBytes = 1024L;
        link.entityType = "ANIMAL";
        link.entityId = animalId;
        link.expiresAt = Instant.now().plusSeconds(3600);
        link.accessCount = 0;
        link.createdAt = Instant.now();
        return link;
    }
}
