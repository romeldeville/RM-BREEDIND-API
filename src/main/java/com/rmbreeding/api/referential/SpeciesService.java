package com.rmbreeding.api.referential;

import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SpeciesService {

    private final SpeciesRepository speciesRepository;
    private final SpeciesTemplateRepository templateRepository;
    private final BreedRepository breedRepository;
    private final VaccinationPlanRepository vaccinationPlanRepository;
    private final FeedingPlanRepository feedingPlanRepository;
    private final GrowthCurveRepository growthCurveRepository;
    private final AuditService auditService;

    public SpeciesService(SpeciesRepository speciesRepository,
            SpeciesTemplateRepository templateRepository,
            BreedRepository breedRepository,
            VaccinationPlanRepository vaccinationPlanRepository,
            FeedingPlanRepository feedingPlanRepository,
            GrowthCurveRepository growthCurveRepository,
            AuditService auditService) {
        this.speciesRepository = speciesRepository;
        this.templateRepository = templateRepository;
        this.breedRepository = breedRepository;
        this.vaccinationPlanRepository = vaccinationPlanRepository;
        this.feedingPlanRepository = feedingPlanRepository;
        this.growthCurveRepository = growthCurveRepository;
        this.auditService = auditService;
    }

    public List<ReferentialDtos.SpeciesResponse> list(boolean includeArchived) {
        UUID tenantId = TenantContext.requireTenantId();
        List<SpeciesEntity> entities = includeArchived
                ? speciesRepository.findByTenantIdOrderByName(tenantId)
                : speciesRepository.findByTenantIdAndArchivedAtIsNullOrderByName(tenantId);
        return entities.stream().map(this::toResponse).toList();
    }

    public ReferentialDtos.SpeciesResponse get(UUID speciesId) {
        return toResponse(requireSpecies(speciesId, TenantContext.requireTenantId()));
    }

    @Transactional
    public ReferentialDtos.SpeciesResponse create(ReferentialDtos.SpeciesRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        if (speciesRepository.existsByTenantIdAndCode(tenantId, request.code())) {
            throw new ConflictException("Species code already used in this tenant");
        }
        Instant now = Instant.now();
        SpeciesEntity species = new SpeciesEntity();
        species.id = UUID.randomUUID();
        species.tenantId = tenantId;
        species.name = request.name();
        species.code = request.code();
        species.productionType = request.productionType();
        species.trackingMode = request.trackingMode();
        species.gestationDays = request.gestationDays();
        species.heatCycleDays = request.heatCycleDays();
        species.weaningAgeDays = request.weaningAgeDays();
        species.incubationDays = request.incubationDays();
        species.settings = copy(request.settings());
        species.createdAt = now;
        species.updatedAt = now;
        speciesRepository.save(species);
        auditService.record("SPECIES_CREATED", "species", species.id);
        return toResponse(species);
    }

    @Transactional
    public ReferentialDtos.SpeciesResponse patch(UUID speciesId, ReferentialDtos.SpeciesPatchRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        SpeciesEntity species = requireSpecies(speciesId, tenantId);
        if (request.name() != null) {
            species.name = request.name();
        }
        if (request.productionType() != null) {
            species.productionType = request.productionType();
        }
        if (request.trackingMode() != null) {
            species.trackingMode = request.trackingMode();
        }
        if (request.gestationDays() != null) {
            species.gestationDays = request.gestationDays();
        }
        if (request.heatCycleDays() != null) {
            species.heatCycleDays = request.heatCycleDays();
        }
        if (request.weaningAgeDays() != null) {
            species.weaningAgeDays = request.weaningAgeDays();
        }
        if (request.incubationDays() != null) {
            species.incubationDays = request.incubationDays();
        }
        if (request.settings() != null) {
            species.settings = copy(request.settings());
        }
        species.updatedAt = Instant.now();
        speciesRepository.save(species);
        auditService.record("SPECIES_UPDATED", "species", species.id);
        return toResponse(species);
    }

    @Transactional
    public void archive(UUID speciesId) {
        UUID tenantId = TenantContext.requireTenantId();
        SpeciesEntity species = requireSpecies(speciesId, tenantId);
        if (species.archivedAt == null) {
            species.archivedAt = Instant.now();
            species.updatedAt = species.archivedAt;
            speciesRepository.save(species);
            auditService.record("SPECIES_ARCHIVED", "species", species.id);
        }
    }

    public List<ReferentialDtos.TemplateResponse> templates(UUID speciesId) {
        UUID tenantId = TenantContext.requireTenantId();
        requireSpecies(speciesId, tenantId);
        return templateRepository.findByTenantIdAndSpeciesIdOrderByName(tenantId, speciesId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public ReferentialDtos.TemplateResponse createTemplate(UUID speciesId, ReferentialDtos.TemplateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        requireSpecies(speciesId, tenantId);
        SpeciesTemplateEntity template = new SpeciesTemplateEntity();
        template.id = UUID.randomUUID();
        template.tenantId = tenantId;
        template.speciesId = speciesId;
        template.name = request.name();
        template.config = copy(request.config());
        templateRepository.save(template);
        return toResponse(template);
    }

    @Transactional
    public void deleteTemplate(UUID templateId) {
        UUID tenantId = TenantContext.requireTenantId();
        SpeciesTemplateEntity template = templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new NotFoundException("Species template not found"));
        templateRepository.delete(template);
    }

    public List<ReferentialDtos.BreedResponse> breeds(UUID speciesId) {
        UUID tenantId = TenantContext.requireTenantId();
        requireSpecies(speciesId, tenantId);
        return breedRepository.findByTenantIdAndSpeciesIdAndArchivedAtIsNullOrderByName(tenantId, speciesId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public ReferentialDtos.BreedResponse createBreed(UUID speciesId, ReferentialDtos.BreedRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        requireSpecies(speciesId, tenantId);
        if (breedRepository.existsByTenantIdAndSpeciesIdAndName(tenantId, speciesId, request.name())) {
            throw new ConflictException("Breed already exists for this species");
        }
        BreedEntity breed = new BreedEntity();
        breed.id = UUID.randomUUID();
        breed.tenantId = tenantId;
        breed.speciesId = speciesId;
        breed.name = request.name();
        breed.code = request.code();
        breedRepository.save(breed);
        return toResponse(breed);
    }

    @Transactional
    public ReferentialDtos.BreedResponse updateBreed(UUID breedId, ReferentialDtos.BreedRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        BreedEntity breed = breedRepository.findByIdAndTenantId(breedId, tenantId)
                .orElseThrow(() -> new NotFoundException("Breed not found"));
        breed.name = request.name();
        breed.code = request.code();
        breedRepository.save(breed);
        return toResponse(breed);
    }

    @Transactional
    public void archiveBreed(UUID breedId) {
        UUID tenantId = TenantContext.requireTenantId();
        BreedEntity breed = breedRepository.findByIdAndTenantId(breedId, tenantId)
                .orElseThrow(() -> new NotFoundException("Breed not found"));
        breed.archivedAt = Instant.now();
        breedRepository.save(breed);
    }

    public List<ReferentialDtos.VaccinationPlanResponse> vaccinationPlans(UUID speciesId) {
        UUID tenantId = TenantContext.requireTenantId();
        requireSpecies(speciesId, tenantId);
        return vaccinationPlanRepository.findByTenantIdAndSpeciesIdOrderByName(tenantId, speciesId).stream()
                .map(this::toResponse).toList();
    }

    public List<ReferentialDtos.VaccinationPlanResponse> allVaccinationPlans() {
        UUID tenantId = TenantContext.requireTenantId();
        return vaccinationPlanRepository.findByTenantIdOrderByName(tenantId).stream()
                .map(this::toResponse).toList();
    }

    public ReferentialDtos.VaccinationPlanResponse requireVaccinationPlan(UUID planId) {
        UUID tenantId = TenantContext.requireTenantId();
        return vaccinationPlanRepository.findByIdAndTenantId(planId, tenantId)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Vaccination plan not found"));
    }

    @Transactional
    public ReferentialDtos.VaccinationPlanResponse createVaccinationPlan(
            UUID speciesId, ReferentialDtos.VaccinationPlanRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        requireSpecies(speciesId, tenantId);
        VaccinationPlanEntity plan = new VaccinationPlanEntity();
        plan.id = UUID.randomUUID();
        plan.tenantId = tenantId;
        plan.speciesId = speciesId;
        apply(plan, request);
        vaccinationPlanRepository.save(plan);
        return toResponse(plan);
    }

    @Transactional
    public ReferentialDtos.VaccinationPlanResponse updateVaccinationPlan(
            UUID planId, ReferentialDtos.VaccinationPlanRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        VaccinationPlanEntity plan = vaccinationPlanRepository.findByIdAndTenantId(planId, tenantId)
                .orElseThrow(() -> new NotFoundException("Vaccination plan not found"));
        apply(plan, request);
        vaccinationPlanRepository.save(plan);
        return toResponse(plan);
    }

    @Transactional
    public void deleteVaccinationPlan(UUID planId) {
        UUID tenantId = TenantContext.requireTenantId();
        VaccinationPlanEntity plan = vaccinationPlanRepository.findByIdAndTenantId(planId, tenantId)
                .orElseThrow(() -> new NotFoundException("Vaccination plan not found"));
        vaccinationPlanRepository.delete(plan);
    }

    public List<ReferentialDtos.FeedingPlanResponse> allFeedingPlans() {
        UUID tenantId = TenantContext.requireTenantId();
        return feedingPlanRepository.findByTenantIdOrderByName(tenantId).stream()
                .map(this::toResponse).toList();
    }

    public ReferentialDtos.FeedingPlanResponse requireFeedingPlan(UUID planId) {
        UUID tenantId = TenantContext.requireTenantId();
        return feedingPlanRepository.findByIdAndTenantId(planId, tenantId)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Feeding plan not found"));
    }

    public List<ReferentialDtos.FeedingPlanResponse> feedingPlans(UUID speciesId) {
        UUID tenantId = TenantContext.requireTenantId();
        requireSpecies(speciesId, tenantId);
        return feedingPlanRepository.findByTenantIdAndSpeciesIdOrderByName(tenantId, speciesId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public ReferentialDtos.FeedingPlanResponse createFeedingPlan(
            UUID speciesId, ReferentialDtos.FeedingPlanRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        requireSpecies(speciesId, tenantId);
        FeedingPlanEntity plan = new FeedingPlanEntity();
        plan.id = UUID.randomUUID();
        plan.tenantId = tenantId;
        plan.speciesId = speciesId;
        apply(plan, request);
        feedingPlanRepository.save(plan);
        return toResponse(plan);
    }

    @Transactional
    public ReferentialDtos.FeedingPlanResponse updateFeedingPlan(
            UUID planId, ReferentialDtos.FeedingPlanRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        FeedingPlanEntity plan = feedingPlanRepository.findByIdAndTenantId(planId, tenantId)
                .orElseThrow(() -> new NotFoundException("Feeding plan not found"));
        apply(plan, request);
        feedingPlanRepository.save(plan);
        return toResponse(plan);
    }

    @Transactional
    public void deleteFeedingPlan(UUID planId) {
        UUID tenantId = TenantContext.requireTenantId();
        FeedingPlanEntity plan = feedingPlanRepository.findByIdAndTenantId(planId, tenantId)
                .orElseThrow(() -> new NotFoundException("Feeding plan not found"));
        feedingPlanRepository.delete(plan);
    }

    public List<ReferentialDtos.GrowthCurveResponse> growthCurves(UUID speciesId) {
        UUID tenantId = TenantContext.requireTenantId();
        requireSpecies(speciesId, tenantId);
        return growthCurveRepository.findByTenantIdAndSpeciesIdOrderByName(tenantId, speciesId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public ReferentialDtos.GrowthCurveResponse createGrowthCurve(
            UUID speciesId, ReferentialDtos.GrowthCurveRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        requireSpecies(speciesId, tenantId);
        GrowthCurveEntity curve = new GrowthCurveEntity();
        curve.id = UUID.randomUUID();
        curve.tenantId = tenantId;
        curve.speciesId = speciesId;
        curve.breedId = request.breedId();
        curve.name = request.name();
        curve.points = copyList(request.points());
        growthCurveRepository.save(curve);
        return toResponse(curve);
    }

    @Transactional
    public void deleteGrowthCurve(UUID curveId) {
        UUID tenantId = TenantContext.requireTenantId();
        GrowthCurveEntity curve = growthCurveRepository.findByIdAndTenantId(curveId, tenantId)
                .orElseThrow(() -> new NotFoundException("Growth curve not found"));
        growthCurveRepository.delete(curve);
    }

    public SpeciesEntity requireSpecies(UUID speciesId, UUID tenantId) {
        return speciesRepository.findByIdAndTenantId(speciesId, tenantId)
                .orElseThrow(() -> new NotFoundException("Species not found"));
    }

    private void apply(VaccinationPlanEntity plan, ReferentialDtos.VaccinationPlanRequest request) {
        plan.name = request.name();
        plan.productId = request.productId();
        plan.ageDays = request.ageDays();
        plan.boosterDays = request.boosterDays();
        plan.schedule = copyList(request.schedule());
        plan.active = request.active() == null || request.active();
    }

    private void apply(FeedingPlanEntity plan, ReferentialDtos.FeedingPlanRequest request) {
        plan.name = request.name();
        plan.stages = copyList(request.stages());
        plan.active = request.active() == null || request.active();
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? Map.of() : Map.copyOf(source);
    }

    private List<Map<String, Object>> copyList(List<Map<String, Object>> source) {
        return source == null ? List.of() : new ArrayList<>(source);
    }

    private ReferentialDtos.SpeciesResponse toResponse(SpeciesEntity species) {
        return new ReferentialDtos.SpeciesResponse(species.id, species.name, species.code,
                species.productionType, species.trackingMode, species.gestationDays, species.heatCycleDays,
                species.weaningAgeDays, species.incubationDays, species.settings, species.archivedAt,
                species.createdAt, species.updatedAt);
    }

    private ReferentialDtos.TemplateResponse toResponse(SpeciesTemplateEntity template) {
        return new ReferentialDtos.TemplateResponse(template.id, template.speciesId, template.name,
                template.config);
    }

    private ReferentialDtos.BreedResponse toResponse(BreedEntity breed) {
        return new ReferentialDtos.BreedResponse(breed.id, breed.speciesId, breed.name, breed.code,
                breed.archivedAt);
    }

    private ReferentialDtos.VaccinationPlanResponse toResponse(VaccinationPlanEntity plan) {
        return new ReferentialDtos.VaccinationPlanResponse(plan.id, plan.speciesId, plan.name, plan.productId,
                plan.ageDays, plan.boosterDays, plan.schedule, plan.active);
    }

    private ReferentialDtos.FeedingPlanResponse toResponse(FeedingPlanEntity plan) {
        return new ReferentialDtos.FeedingPlanResponse(plan.id, plan.speciesId, plan.name, plan.stages,
                plan.active);
    }

    private ReferentialDtos.GrowthCurveResponse toResponse(GrowthCurveEntity curve) {
        return new ReferentialDtos.GrowthCurveResponse(curve.id, curve.speciesId, curve.breedId, curve.name,
                curve.points);
    }
}
