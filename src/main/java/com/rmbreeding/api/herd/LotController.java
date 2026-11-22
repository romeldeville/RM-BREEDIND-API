package com.rmbreeding.api.herd;

import com.rmbreeding.api.shared.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/lots")
public class LotController {

    private final LotService lotService;

    public LotController(LotService lotService) {
        this.lotService = lotService;
    }

    @GetMapping
    public PageResponse<LotDtos.LotResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID speciesId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return lotService.list(status, speciesId, limit, cursor);
    }

    @PostMapping
    public ResponseEntity<LotDtos.LotResponse> create(
            @Valid @RequestBody LotDtos.LotCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lotService.create(request));
    }

    @GetMapping("/{lotId}")
    public LotDtos.LotResponse get(@PathVariable UUID lotId) {
        return lotService.get(lotId);
    }

    @PatchMapping("/{lotId}")
    public LotDtos.LotResponse patch(@PathVariable UUID lotId,
            @Valid @RequestBody LotDtos.LotPatchRequest request) {
        return lotService.patch(lotId, request);
    }

    @PostMapping("/{lotId}/movements")
    public ResponseEntity<LotDtos.LotMovementResponse> move(@PathVariable UUID lotId,
            @Valid @RequestBody LotDtos.LotMovementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lotService.move(lotId, request));
    }

    @GetMapping("/{lotId}/movements")
    public List<LotDtos.LotMovementResponse> movements(@PathVariable UUID lotId) {
        return lotService.movements(lotId);
    }

    @PostMapping("/{lotId}/close")
    public LotDtos.LotClosureResponse close(@PathVariable UUID lotId) {
        return lotService.close(lotId);
    }

    @PostMapping("/{lotId}/split")
    public ResponseEntity<LotDtos.LotResponse> split(@PathVariable UUID lotId,
            @Valid @RequestBody LotDtos.LotSplitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lotService.split(lotId, request));
    }

    @PostMapping("/{lotId}/merge")
    public LotDtos.LotResponse merge(@PathVariable UUID lotId,
            @Valid @RequestBody LotDtos.LotMergeRequest request) {
        return lotService.merge(lotId, request.sourceLotId());
    }
}
