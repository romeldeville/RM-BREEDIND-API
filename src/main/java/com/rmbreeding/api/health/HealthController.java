package com.rmbreeding.api.health;

import com.rmbreeding.api.referential.ReferentialDtos;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    // P7-01 Products

    @GetMapping("/products")
    public List<HealthDtos.ProductResponse> listProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived) {
        return healthService.listProducts(search, type, includeArchived);
    }

    @PostMapping("/products")
    public ResponseEntity<HealthDtos.ProductResponse> createProduct(
            @Valid @RequestBody HealthDtos.ProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(healthService.createProduct(request));
    }

    @GetMapping("/products/{productId}")
    public HealthDtos.ProductResponse getProduct(@PathVariable UUID productId) {
        return healthService.getProduct(productId);
    }

    @PatchMapping("/products/{productId}")
    public HealthDtos.ProductResponse patchProduct(@PathVariable UUID productId,
            @Valid @RequestBody HealthDtos.ProductPatchRequest request) {
        return healthService.patchProduct(productId, request);
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Void> archiveProduct(@PathVariable UUID productId) {
        healthService.archiveProduct(productId);
        return ResponseEntity.noContent().build();
    }

    // P7-02 Vaccination plans

    @GetMapping("/vaccination-plans")
    public List<ReferentialDtos.VaccinationPlanResponse> listVaccinationPlans(
            @RequestParam(required = false) UUID speciesId) {
        return healthService.listVaccinationPlans(speciesId);
    }

    @PostMapping("/vaccination-plans")
    public ResponseEntity<ReferentialDtos.VaccinationPlanResponse> createVaccinationPlan(
            @Valid @RequestBody HealthDtos.VaccinationPlanCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(healthService.createVaccinationPlan(request));
    }

    // P7-03 Vaccinations

    @PostMapping("/vaccinations")
    public ResponseEntity<HealthDtos.VaccinationResponse> recordVaccination(
            @Valid @RequestBody HealthDtos.VaccinationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(healthService.recordVaccination(request));
    }

    @GetMapping("/vaccinations")
    public List<HealthDtos.VaccinationResponse> listVaccinations(
            @RequestParam(required = false) UUID animalId,
            @RequestParam(required = false) UUID lotId) {
        return healthService.listVaccinations(animalId, lotId);
    }

    // P7-04 Treatments + administrations

    @GetMapping("/treatments")
    public List<HealthDtos.TreatmentResponse> listTreatments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID animalId,
            @RequestParam(required = false) UUID lotId) {
        return healthService.listTreatments(status, animalId, lotId);
    }

    @PostMapping("/treatments")
    public ResponseEntity<HealthDtos.TreatmentResponse> createTreatment(
            @Valid @RequestBody HealthDtos.TreatmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(healthService.createTreatment(request));
    }

    @GetMapping("/treatments/{treatmentId}")
    public HealthDtos.TreatmentResponse getTreatment(@PathVariable UUID treatmentId) {
        return healthService.getTreatment(treatmentId);
    }

    @PatchMapping("/treatments/{treatmentId}")
    public HealthDtos.TreatmentResponse patchTreatment(@PathVariable UUID treatmentId,
            @Valid @RequestBody HealthDtos.TreatmentPatchRequest request) {
        return healthService.patchTreatment(treatmentId, request);
    }

    @PostMapping("/treatments/{treatmentId}/administrations")
    public ResponseEntity<HealthDtos.AdministrationResponse> addAdministration(@PathVariable UUID treatmentId,
            @Valid @RequestBody HealthDtos.AdministrationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(healthService.addAdministration(treatmentId, request));
    }

    // P7-05 Withdrawals + P7-06 sale eligibility

    @GetMapping("/withdrawals")
    public List<HealthDtos.WithdrawalResponse> listWithdrawals(
            @RequestParam(required = false) String status) {
        return healthService.listWithdrawals(status);
    }

    @GetMapping("/sale-eligibility")
    public HealthDtos.SaleEligibilityResponse saleEligibility(
            @RequestParam(required = false) UUID animalId,
            @RequestParam(required = false) UUID lotId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) LocalDate date) {
        return healthService.checkSaleEligibility(animalId, lotId, type, date);
    }

    // P7-07 Mortality

    @PostMapping("/mortalities")
    public ResponseEntity<HealthDtos.MortalityResponse> recordMortality(
            @Valid @RequestBody HealthDtos.MortalityCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(healthService.recordMortality(request));
    }

    @GetMapping("/mortalities")
    public List<HealthDtos.MortalityResponse> listMortalities(
            @RequestParam(required = false) UUID animalId) {
        return healthService.listMortalities(animalId);
    }

    // P7-08 Health events

    @PostMapping("/events")
    public ResponseEntity<HealthDtos.HealthEventResponse> recordHealthEvent(
            @Valid @RequestBody HealthDtos.HealthEventCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(healthService.recordHealthEvent(request));
    }

    @GetMapping("/events")
    public List<HealthDtos.HealthEventResponse> listHealthEvents(
            @RequestParam(required = false) UUID animalId) {
        return healthService.listHealthEvents(animalId);
    }

    // P7-09 Quarantine

    @GetMapping("/quarantines")
    public List<HealthDtos.QuarantineResponse> listQuarantines(
            @RequestParam(required = false) String status) {
        return healthService.listQuarantines(status);
    }

    @PostMapping("/quarantines")
    public ResponseEntity<HealthDtos.QuarantineResponse> createQuarantine(
            @Valid @RequestBody HealthDtos.QuarantineCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(healthService.createQuarantine(request));
    }

    @PostMapping("/quarantines/{quarantineId}/release")
    public HealthDtos.QuarantineResponse releaseQuarantine(@PathVariable UUID quarantineId) {
        return healthService.releaseQuarantine(quarantineId);
    }

    // P7-10 Health book

    @GetMapping("/animals/{animalId}/book")
    public HealthDtos.HealthBookResponse healthBook(@PathVariable UUID animalId) {
        return healthService.healthBook(animalId);
    }
}
