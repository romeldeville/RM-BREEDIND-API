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
@RequestMapping("/api/v1/animals")
public class AnimalController {

    private final AnimalService animalService;

    public AnimalController(AnimalService animalService) {
        this.animalService = animalService;
    }

    @GetMapping
    public PageResponse<HerdDtos.AnimalResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID speciesId,
            @RequestParam(required = false) UUID penId,
            @RequestParam(required = false) UUID lotId,
            @RequestParam(required = false) String sex,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return animalService.list(search, status, speciesId, penId, lotId, sex, limit, cursor);
    }

    @PostMapping
    public ResponseEntity<HerdDtos.AnimalResponse> create(
            @Valid @RequestBody HerdDtos.AnimalCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(animalService.create(request));
    }

    @GetMapping("/{animalId}")
    public HerdDtos.AnimalResponse get(@PathVariable UUID animalId) {
        return animalService.get(animalId);
    }

    @PatchMapping("/{animalId}")
    public HerdDtos.AnimalResponse patch(@PathVariable UUID animalId,
            @Valid @RequestBody HerdDtos.AnimalPatchRequest request) {
        return animalService.patch(animalId, request);
    }

    @PostMapping("/{animalId}/status")
    public HerdDtos.AnimalStatusResponse changeStatus(@PathVariable UUID animalId,
            @Valid @RequestBody HerdDtos.AnimalStatusRequest request) {
        return animalService.changeStatus(animalId, request);
    }

    @GetMapping("/{animalId}/status-history")
    public List<HerdDtos.AnimalStatusResponse> statusHistory(@PathVariable UUID animalId) {
        return animalService.statusHistory(animalId);
    }

    @PostMapping("/{lotId}/individualize")
    public ResponseEntity<HerdDtos.AnimalResponse> individualize(@PathVariable UUID lotId,
            @Valid @RequestBody LotDtos.IndividualizeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(animalService.individualize(lotId, request));
    }
}
