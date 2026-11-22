package com.rmbreeding.api.reproduction;

import com.rmbreeding.api.herd.LotDtos;
import com.rmbreeding.api.shared.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reproduction")
public class ReproductionController {

    private final ReproductionService reproductionService;

    public ReproductionController(ReproductionService reproductionService) {
        this.reproductionService = reproductionService;
    }

    @GetMapping("/cycles")
    public PageResponse<ReproductionDtos.CycleResponse> listCycles(
            @RequestParam(required = false) UUID femaleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return reproductionService.listCycles(femaleId, status, limit, cursor);
    }

    @PostMapping("/cycles")
    public ResponseEntity<ReproductionDtos.CycleResponse> createCycle(
            @Valid @RequestBody ReproductionDtos.CycleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reproductionService.createCycle(request));
    }

    @GetMapping("/cycles/{cycleId}")
    public ReproductionDtos.CycleDetailResponse getCycle(@PathVariable UUID cycleId) {
        return reproductionService.getCycle(cycleId);
    }

    @PostMapping("/heats")
    public ResponseEntity<ReproductionDtos.HeatResponse> recordHeat(
            @Valid @RequestBody ReproductionDtos.HeatCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reproductionService.recordHeat(request));
    }

    @PostMapping("/services")
    public ResponseEntity<ReproductionDtos.MatingResponse> recordMating(
            @Valid @RequestBody ReproductionDtos.MatingCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reproductionService.recordMating(request));
    }

    @PostMapping("/diagnoses")
    public ResponseEntity<ReproductionDtos.DiagnosisResponse> recordDiagnosis(
            @Valid @RequestBody ReproductionDtos.DiagnosisCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reproductionService.recordDiagnosis(request));
    }

    @PostMapping("/abortions")
    public ResponseEntity<ReproductionDtos.AbortionResponse> recordAbortion(
            @Valid @RequestBody ReproductionDtos.AbortionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reproductionService.recordAbortion(request));
    }

    @PostMapping("/farrowings")
    public ResponseEntity<ReproductionDtos.BirthCreationResponse> recordBirth(
            @Valid @RequestBody ReproductionDtos.BirthCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reproductionService.recordBirth(request));
    }

    @PostMapping("/weanings")
    public ResponseEntity<ReproductionDtos.WeaningResponse> recordWeaning(
            @Valid @RequestBody ReproductionDtos.WeaningCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reproductionService.recordWeaning(request));
    }

    @PostMapping("/litters/{litterId}/lot")
    public ResponseEntity<LotDtos.LotResponse> createLotFromLitter(@PathVariable UUID litterId,
            @Valid @RequestBody ReproductionDtos.LitterLotCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reproductionService.createLotFromLitter(litterId, request));
    }
}
