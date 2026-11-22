package com.rmbreeding.api.referential;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/species")
public class SpeciesController {

    private final SpeciesService speciesService;

    public SpeciesController(SpeciesService speciesService) {
        this.speciesService = speciesService;
    }

    @GetMapping
    public List<ReferentialDtos.SpeciesResponse> list(
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return speciesService.list(includeArchived);
    }

    @PostMapping
    public ResponseEntity<ReferentialDtos.SpeciesResponse> create(
            @Valid @RequestBody ReferentialDtos.SpeciesRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(speciesService.create(request));
    }

    @GetMapping("/{speciesId}")
    public ReferentialDtos.SpeciesResponse get(@PathVariable UUID speciesId) {
        return speciesService.get(speciesId);
    }

    @PatchMapping("/{speciesId}")
    public ReferentialDtos.SpeciesResponse patch(@PathVariable UUID speciesId,
            @Valid @RequestBody ReferentialDtos.SpeciesPatchRequest request) {
        return speciesService.patch(speciesId, request);
    }

    @DeleteMapping("/{speciesId}")
    public ResponseEntity<Void> archive(@PathVariable UUID speciesId) {
        speciesService.archive(speciesId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{speciesId}/templates")
    public List<ReferentialDtos.TemplateResponse> templates(@PathVariable UUID speciesId) {
        return speciesService.templates(speciesId);
    }

    @PostMapping("/{speciesId}/templates")
    public ResponseEntity<ReferentialDtos.TemplateResponse> createTemplate(@PathVariable UUID speciesId,
            @Valid @RequestBody ReferentialDtos.TemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(speciesService.createTemplate(speciesId, request));
    }

    @DeleteMapping("/templates/{templateId}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID templateId) {
        speciesService.deleteTemplate(templateId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{speciesId}/breeds")
    public List<ReferentialDtos.BreedResponse> breeds(@PathVariable UUID speciesId) {
        return speciesService.breeds(speciesId);
    }

    @PostMapping("/{speciesId}/breeds")
    public ResponseEntity<ReferentialDtos.BreedResponse> createBreed(@PathVariable UUID speciesId,
            @Valid @RequestBody ReferentialDtos.BreedRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(speciesService.createBreed(speciesId, request));
    }

    @PutMapping("/breeds/{breedId}")
    public ReferentialDtos.BreedResponse updateBreed(@PathVariable UUID breedId,
            @Valid @RequestBody ReferentialDtos.BreedRequest request) {
        return speciesService.updateBreed(breedId, request);
    }

    @DeleteMapping("/breeds/{breedId}")
    public ResponseEntity<Void> archiveBreed(@PathVariable UUID breedId) {
        speciesService.archiveBreed(breedId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{speciesId}/vaccination-plans")
    public List<ReferentialDtos.VaccinationPlanResponse> vaccinationPlans(@PathVariable UUID speciesId) {
        return speciesService.vaccinationPlans(speciesId);
    }

    @PostMapping("/{speciesId}/vaccination-plans")
    public ResponseEntity<ReferentialDtos.VaccinationPlanResponse> createVaccinationPlan(
            @PathVariable UUID speciesId, @Valid @RequestBody ReferentialDtos.VaccinationPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(speciesService.createVaccinationPlan(speciesId, request));
    }

    @PutMapping("/vaccination-plans/{planId}")
    public ReferentialDtos.VaccinationPlanResponse updateVaccinationPlan(@PathVariable UUID planId,
            @Valid @RequestBody ReferentialDtos.VaccinationPlanRequest request) {
        return speciesService.updateVaccinationPlan(planId, request);
    }

    @DeleteMapping("/vaccination-plans/{planId}")
    public ResponseEntity<Void> deleteVaccinationPlan(@PathVariable UUID planId) {
        speciesService.deleteVaccinationPlan(planId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{speciesId}/feeding-plans")
    public List<ReferentialDtos.FeedingPlanResponse> feedingPlans(@PathVariable UUID speciesId) {
        return speciesService.feedingPlans(speciesId);
    }

    @PostMapping("/{speciesId}/feeding-plans")
    public ResponseEntity<ReferentialDtos.FeedingPlanResponse> createFeedingPlan(
            @PathVariable UUID speciesId, @Valid @RequestBody ReferentialDtos.FeedingPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(speciesService.createFeedingPlan(speciesId, request));
    }

    @PutMapping("/feeding-plans/{planId}")
    public ReferentialDtos.FeedingPlanResponse updateFeedingPlan(@PathVariable UUID planId,
            @Valid @RequestBody ReferentialDtos.FeedingPlanRequest request) {
        return speciesService.updateFeedingPlan(planId, request);
    }

    @DeleteMapping("/feeding-plans/{planId}")
    public ResponseEntity<Void> deleteFeedingPlan(@PathVariable UUID planId) {
        speciesService.deleteFeedingPlan(planId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{speciesId}/growth-curves")
    public List<ReferentialDtos.GrowthCurveResponse> growthCurves(@PathVariable UUID speciesId) {
        return speciesService.growthCurves(speciesId);
    }

    @PostMapping("/{speciesId}/growth-curves")
    public ResponseEntity<ReferentialDtos.GrowthCurveResponse> createGrowthCurve(
            @PathVariable UUID speciesId, @Valid @RequestBody ReferentialDtos.GrowthCurveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(speciesService.createGrowthCurve(speciesId, request));
    }

    @DeleteMapping("/growth-curves/{curveId}")
    public ResponseEntity<Void> deleteGrowthCurve(@PathVariable UUID curveId) {
        speciesService.deleteGrowthCurve(curveId);
        return ResponseEntity.noContent().build();
    }
}
