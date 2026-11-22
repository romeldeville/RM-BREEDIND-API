package com.rmbreeding.api.files;

import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    // P14-02 - presigned upload

    @PostMapping("/presign")
    public AttachmentDtos.PresignResponse presign(
            @jakarta.validation.Valid @RequestBody AttachmentDtos.PresignRequest request) {
        return attachmentService.presign(request);
    }

    // P14-03 - metadata confirmation

    @PostMapping("/{attachmentId}/confirm")
    public AttachmentDtos.AttachmentResponse confirm(@PathVariable UUID attachmentId,
            @jakarta.validation.Valid @RequestBody(required = false) AttachmentDtos.ConfirmRequest request) {
        return attachmentService.confirm(attachmentId, request);
    }

    @GetMapping
    public List<AttachmentDtos.AttachmentResponse> list(
            @RequestParam String entityType,
            @RequestParam UUID entityId) {
        return attachmentService.list(entityType, entityId);
    }

    @GetMapping("/{attachmentId}")
    public AttachmentDtos.AttachmentResponse get(@PathVariable UUID attachmentId) {
        return attachmentService.get(attachmentId);
    }

    @GetMapping("/{attachmentId}/download-url")
    public AttachmentDtos.DownloadUrlResponse downloadUrl(@PathVariable UUID attachmentId) {
        return attachmentService.downloadUrl(attachmentId);
    }

    @DeleteMapping("/{attachmentId}")
    public org.springframework.http.ResponseEntity<Void> delete(@PathVariable UUID attachmentId) {
        attachmentService.delete(attachmentId);
        return org.springframework.http.ResponseEntity.noContent().build();
    }
}
