package com.rmbreeding.api.files;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Public, unauthenticated download endpoint. The signed token carries the tenant and link id.
 */
@RestController
@RequestMapping("/api/v1/public/shares")
public class PublicShareController {

    private final ShareService shareService;

    public PublicShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping("/{token}")
    public ResponseEntity<byte[]> download(@PathVariable String token) {
        ShareService.PublicShareFile file = shareService.open(token);
        String filename = file.filename() == null ? "document" : file.filename();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType;
        try {
            mediaType = file.mimeType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(file.mimeType());
        } catch (RuntimeException ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(file.content().length)
                .body(file.content());
    }
}
