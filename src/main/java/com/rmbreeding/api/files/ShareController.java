package com.rmbreeding.api.files;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/shares")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping
    public ResponseEntity<ShareDtos.ShareLinkResponse> create(
            @Valid @RequestBody ShareDtos.ShareCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shareService.create(request));
    }

    @GetMapping
    public List<ShareDtos.ShareLinkResponse> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId) {
        return shareService.list(entityType, entityId);
    }

    @PostMapping("/{shareId}/revoke")
    public ShareDtos.ShareLinkResponse revoke(@PathVariable UUID shareId) {
        return shareService.revoke(shareId);
    }

    @DeleteMapping("/{shareId}")
    public ResponseEntity<Void> delete(@PathVariable UUID shareId) {
        shareService.delete(shareId);
        return ResponseEntity.noContent().build();
    }
}
