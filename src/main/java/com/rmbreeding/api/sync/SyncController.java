package com.rmbreeding.api.sync;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/push")
    public SyncDtos.PushResponse push(@Valid @RequestBody SyncDtos.PushRequest request) {
        return syncService.push(request);
    }

    @GetMapping("/pull")
    public SyncDtos.PullResponse pull(
            @RequestParam(defaultValue = "0") long since,
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(required = false) String deviceId) {
        return syncService.pull(since, limit, deviceId);
    }

    @GetMapping("/status")
    public SyncDtos.StatusResponse status(@RequestParam String deviceId) {
        return syncService.status(deviceId);
    }

    @GetMapping("/conflicts")
    public List<SyncDtos.ConflictView> conflicts(
            @RequestParam(defaultValue = "false") boolean includeResolved) {
        return syncService.conflicts(includeResolved);
    }

    @PostMapping("/conflicts/{id}/resolve")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','PLATFORM_ADMIN')")
    public SyncDtos.ConflictView resolve(@PathVariable UUID id,
            @Valid @RequestBody SyncDtos.ResolveConflictRequest request) {
        return syncService.resolve(id, request);
    }

    @PostMapping("/projections/rebuild")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','PLATFORM_ADMIN')")
    public SyncDtos.RebuildResponse rebuild() {
        return syncService.rebuildProjections();
    }
}
