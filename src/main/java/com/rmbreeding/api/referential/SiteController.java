package com.rmbreeding.api.referential;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class SiteController {

    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping("/buildings")
    public List<ReferentialDtos.BuildingResponse> buildings(@RequestParam UUID farmId) {
        return siteService.buildings(farmId);
    }

    @PostMapping("/buildings")
    public ResponseEntity<ReferentialDtos.BuildingResponse> createBuilding(
            @Valid @RequestBody ReferentialDtos.BuildingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(siteService.createBuilding(request));
    }

    @PatchMapping("/buildings/{buildingId}")
    public ReferentialDtos.BuildingResponse patchBuilding(@PathVariable UUID buildingId,
            @Valid @RequestBody ReferentialDtos.BuildingPatchRequest request) {
        return siteService.patchBuilding(buildingId, request);
    }

    @DeleteMapping("/buildings/{buildingId}")
    public ResponseEntity<Void> archiveBuilding(@PathVariable UUID buildingId) {
        siteService.archiveBuilding(buildingId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pens")
    public List<ReferentialDtos.PenResponse> pens(@RequestParam UUID buildingId) {
        return siteService.pens(buildingId);
    }

    @PostMapping("/pens")
    public ResponseEntity<ReferentialDtos.PenResponse> createPen(
            @Valid @RequestBody ReferentialDtos.PenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(siteService.createPen(request));
    }

    @PatchMapping("/pens/{penId}")
    public ReferentialDtos.PenResponse patchPen(@PathVariable UUID penId,
            @Valid @RequestBody ReferentialDtos.PenPatchRequest request) {
        return siteService.patchPen(penId, request);
    }

    @DeleteMapping("/pens/{penId}")
    public ResponseEntity<Void> archivePen(@PathVariable UUID penId) {
        siteService.archivePen(penId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pens/{penId}/occupancy")
    public ReferentialDtos.PenOccupancyResponse occupancy(@PathVariable UUID penId) {
        return siteService.occupancy(penId);
    }

    @GetMapping("/pen-movements")
    public List<ReferentialDtos.PenMovementResponse> movements(
            @RequestParam(required = false) UUID penId) {
        return siteService.movements(penId);
    }

    @PostMapping("/pen-movements")
    public ResponseEntity<ReferentialDtos.PenMovementResponse> move(
            @Valid @RequestBody ReferentialDtos.PenMovementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(siteService.move(request));
    }
}
