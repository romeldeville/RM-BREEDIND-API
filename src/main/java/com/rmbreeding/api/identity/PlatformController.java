package com.rmbreeding.api.identity;

import com.rmbreeding.api.identity.PlatformDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformController {

    private final SupportService supportService;
    private final PlatformService platformService;

    public PlatformController(SupportService supportService, PlatformService platformService) {
        this.supportService = supportService;
        this.platformService = platformService;
    }

    @PostMapping("/tenants/{tenantId}/support-sessions")
    public ResponseEntity<IdentityDtos.TokenPair> start(@AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID tenantId, @Valid @RequestBody IdentityDtos.SupportSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supportService.start(principal.userId(), tenantId, request.reason()));
    }

    @PostMapping("/support-sessions/{sessionId}/end")
    public ResponseEntity<Void> end(@AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID sessionId) {
        supportService.end(principal.userId(), sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tenants")
    public ResponseEntity<List<PlatformTenantDto>> listTenants(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(platformService.listTenants(search));
    }

    @GetMapping("/tenants/{tenantId}")
    public ResponseEntity<PlatformTenantDto> getTenant(@PathVariable UUID tenantId) {
        PlatformTenantDto detail = platformService.getTenantDetail(tenantId);
        return detail != null ? ResponseEntity.ok(detail) : ResponseEntity.notFound().build();
    }

    @PostMapping("/tenants/{tenantId}/suspend")
    public ResponseEntity<Void> suspendTenant(@PathVariable UUID tenantId) {
        platformService.suspendTenant(tenantId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tenants/{tenantId}/reactivate")
    public ResponseEntity<Void> reactivateTenant(@PathVariable UUID tenantId) {
        platformService.reactivateTenant(tenantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/metrics")
    public ResponseEntity<PlatformMetricsDto> getMetrics() {
        return ResponseEntity.ok(platformService.getMetrics());
    }

    @GetMapping("/announcements")
    public ResponseEntity<List<AnnouncementDto>> listAnnouncements() {
        return ResponseEntity.ok(platformService.listAnnouncements());
    }

    @PostMapping("/announcements")
    public ResponseEntity<AnnouncementDto> createAnnouncement(@RequestBody @Valid CreateAnnouncementRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platformService.createAnnouncement(req));
    }

    @GetMapping("/tenants/export")
    public ResponseEntity<String> exportTenantsCsv() {
        String csv = platformService.exportTenantsCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tenants.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
