package com.rmbreeding.api.health;

import com.rmbreeding.api.herd.AnimalService;
import com.rmbreeding.api.herd.HerdDtos;
import com.rmbreeding.api.herd.HerdLookup;
import com.rmbreeding.api.herd.LotDtos;
import com.rmbreeding.api.herd.LotService;
import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.identity.AuthContext;
import com.rmbreeding.api.referential.ReferentialDtos;
import com.rmbreeding.api.referential.SpeciesService;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class HealthService {

    private final HealthProductRepository productRepository;
    private final VaccinationRepository vaccinationRepository;
    private final TreatmentRepository treatmentRepository;
    private final TreatmentAdministrationRepository administrationRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final HealthEventRepository healthEventRepository;
    private final MortalityRepository mortalityRepository;
    private final QuarantineRepository quarantineRepository;
    private final HerdLookup herdLookup;
    private final LotService lotService;
    private final AnimalService animalService;
    private final SpeciesService speciesService;
    private final AuditService auditService;

    public HealthService(HealthProductRepository productRepository,
            VaccinationRepository vaccinationRepository,
            TreatmentRepository treatmentRepository,
            TreatmentAdministrationRepository administrationRepository,
            WithdrawalRepository withdrawalRepository,
            HealthEventRepository healthEventRepository,
            MortalityRepository mortalityRepository,
            QuarantineRepository quarantineRepository,
            HerdLookup herdLookup,
            LotService lotService,
            AnimalService animalService,
            SpeciesService speciesService,
            AuditService auditService) {
        this.productRepository = productRepository;
        this.vaccinationRepository = vaccinationRepository;
        this.treatmentRepository = treatmentRepository;
        this.administrationRepository = administrationRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.healthEventRepository = healthEventRepository;
        this.mortalityRepository = mortalityRepository;
        this.quarantineRepository = quarantineRepository;
        this.herdLookup = herdLookup;
        this.lotService = lotService;
        this.animalService = animalService;
        this.speciesService = speciesService;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // P7-01 Products
    // ------------------------------------------------------------------

    public List<HealthDtos.ProductResponse> listProducts(String search, String type, boolean includeArchived) {
        UUID tenantId = TenantContext.requireTenantId();
        Specification<HealthProductEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("tenantId"), tenantId));
            if (!includeArchived) {
                predicates.add(builder.isNull(root.get("archivedAt")));
            }
            if (type != null && !type.isBlank()) {
                predicates.add(builder.equal(root.get("type"), type));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(builder.like(builder.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return productRepository.findAll(specification, Sort.by("name")).stream()
                .map(this::toProductResponse).toList();
    }

    @Transactional
    public HealthDtos.ProductResponse createProduct(HealthDtos.ProductCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        HealthProductEntity product = new HealthProductEntity();
        product.id = UUID.randomUUID();
        product.tenantId = tenantId;
        apply(product, request);
        productRepository.save(product);
        auditService.record("HEALTH_PRODUCT_CREATED", "health_product", product.id);
        return toProductResponse(product);
    }

    public HealthDtos.ProductResponse getProduct(UUID productId) {
        return toProductResponse(requireProduct(productId, TenantContext.requireTenantId()));
    }

    @Transactional
    public HealthDtos.ProductResponse patchProduct(UUID productId, HealthDtos.ProductPatchRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        HealthProductEntity product = requireProduct(productId, tenantId);
        if (request.name() != null) {
            product.name = request.name();
        }
        if (request.type() != null) {
            product.type = request.type();
        }
        if (request.speciesAllowed() != null) {
            product.speciesAllowed = new ArrayList<>(request.speciesAllowed());
        }
        if (request.dose() != null) {
            product.dose = request.dose();
        }
        if (request.route() != null) {
            product.route = request.route();
        }
        if (request.withdrawalMeatDays() != null) {
            product.withdrawalMeatDays = request.withdrawalMeatDays();
        }
        if (request.withdrawalEggsDays() != null) {
            product.withdrawalEggsDays = request.withdrawalEggsDays();
        }
        if (request.withdrawalMilkDays() != null) {
            product.withdrawalMilkDays = request.withdrawalMilkDays();
        }
        if (request.shelfLifeDays() != null) {
            product.shelfLifeDays = request.shelfLifeDays();
        }
        if (request.supplier() != null) {
            product.supplier = request.supplier();
        }
        if (request.stockQty() != null) {
            product.stockQty = request.stockQty();
        }
        productRepository.save(product);
        auditService.record("HEALTH_PRODUCT_UPDATED", "health_product", product.id);
        return toProductResponse(product);
    }

    @Transactional
    public void archiveProduct(UUID productId) {
        UUID tenantId = TenantContext.requireTenantId();
        HealthProductEntity product = requireProduct(productId, tenantId);
        product.archivedAt = Instant.now();
        productRepository.save(product);
        auditService.record("HEALTH_PRODUCT_ARCHIVED", "health_product", product.id);
    }

    // ------------------------------------------------------------------
    // P7-02 Vaccination plans (delegates to referential)
    // ------------------------------------------------------------------

    public List<ReferentialDtos.VaccinationPlanResponse> listVaccinationPlans(UUID speciesId) {
        if (speciesId != null) {
            return speciesService.vaccinationPlans(speciesId);
        }
        return speciesService.allVaccinationPlans();
    }

    @Transactional
    public ReferentialDtos.VaccinationPlanResponse createVaccinationPlan(
            HealthDtos.VaccinationPlanCreateRequest request) {
        return speciesService.createVaccinationPlan(request.speciesId(),
                new ReferentialDtos.VaccinationPlanRequest(request.name(), request.productId(),
                        request.ageDays(), request.boosterDays(), request.schedule(), request.active()));
    }

    // ------------------------------------------------------------------
    // P7-03 Vaccinations
    // ------------------------------------------------------------------

    @Transactional
    public HealthDtos.VaccinationResponse recordVaccination(HealthDtos.VaccinationCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        requireTarget(request.animalId(), request.lotId(), tenantId);
        requireProduct(request.productId(), tenantId);
        if (request.planId() != null) {
            speciesService.requireVaccinationPlan(request.planId());
        }

        VaccinationEntity vaccination = new VaccinationEntity();
        vaccination.id = UUID.randomUUID();
        vaccination.tenantId = tenantId;
        vaccination.animalId = request.animalId();
        vaccination.lotId = request.lotId();
        vaccination.productId = request.productId();
        vaccination.planId = request.planId();
        vaccination.vaccineLot = request.vaccineLot();
        vaccination.dose = request.dose();
        vaccination.route = request.route();
        vaccination.count = request.count() == null ? 1 : request.count();
        vaccination.performedAt = request.performedAt();
        vaccination.operatorId = AuthContext.userId();
        vaccination.vetId = request.vetId();
        vaccination.notes = request.notes();
        vaccinationRepository.save(vaccination);
        auditService.record("VACCINATION_RECORDED", "vaccination", vaccination.id);
        return toVaccinationResponse(vaccination);
    }

    public List<HealthDtos.VaccinationResponse> listVaccinations(UUID animalId, UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        List<VaccinationEntity> entities;
        if (animalId != null) {
            entities = vaccinationRepository.findByTenantIdAndAnimalIdOrderByPerformedAtDesc(tenantId, animalId);
        } else if (lotId != null) {
            entities = vaccinationRepository.findByTenantIdAndLotIdOrderByPerformedAtDesc(tenantId, lotId);
        } else {
            entities = vaccinationRepository.findByTenantIdOrderByPerformedAtDesc(tenantId);
        }
        return entities.stream().map(this::toVaccinationResponse).toList();
    }

    // ------------------------------------------------------------------
    // P7-04 Treatments + administrations
    // ------------------------------------------------------------------

    public List<HealthDtos.TreatmentResponse> listTreatments(String status, UUID animalId, UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        List<TreatmentEntity> entities;
        if (animalId != null) {
            entities = treatmentRepository.findByTenantIdAndAnimalIdOrderByStartDateDesc(tenantId, animalId);
        } else if (lotId != null) {
            entities = treatmentRepository.findByTenantIdAndLotIdOrderByStartDateDesc(tenantId, lotId);
        } else if (status != null && !status.isBlank()) {
            entities = treatmentRepository.findByTenantIdAndStatusOrderByStartDateDesc(tenantId, status);
        } else {
            entities = treatmentRepository.findByTenantIdOrderByStartDateDesc(tenantId);
        }
        return entities.stream().map(this::toTreatmentResponse).toList();
    }

    public HealthDtos.TreatmentResponse getTreatment(UUID treatmentId) {
        return toTreatmentResponse(requireTreatment(treatmentId, TenantContext.requireTenantId()));
    }

    @Transactional
    public HealthDtos.TreatmentResponse createTreatment(HealthDtos.TreatmentCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        requireTarget(request.animalId(), request.lotId(), tenantId);
        HealthProductEntity product = requireProduct(request.productId(), tenantId);

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.id = UUID.randomUUID();
        treatment.tenantId = tenantId;
        treatment.animalId = request.animalId();
        treatment.lotId = request.lotId();
        treatment.diagnosis = request.diagnosis();
        treatment.symptoms = request.symptoms();
        treatment.productId = request.productId();
        treatment.dose = request.dose() == null ? product.dose : request.dose();
        treatment.route = request.route() == null ? product.route : request.route();
        treatment.startDate = request.startDate();
        treatment.durationDays = request.durationDays();
        treatment.endDate = request.durationDays() == null ? null
                : request.startDate().plusDays(request.durationDays());
        treatment.status = "ACTIVE";
        treatment.notes = request.notes();
        treatmentRepository.save(treatment);

        createWithdrawals(tenantId, treatment, product);
        auditService.record("TREATMENT_STARTED", "treatment", treatment.id);
        return toTreatmentResponse(treatment);
    }

    @Transactional
    public HealthDtos.TreatmentResponse patchTreatment(UUID treatmentId, HealthDtos.TreatmentPatchRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        TreatmentEntity treatment = requireTreatment(treatmentId, tenantId);
        if (request.endDate() != null) {
            treatment.endDate = request.endDate();
        }
        if (request.notes() != null) {
            treatment.notes = request.notes();
        }
        if (request.status() != null) {
            treatment.status = request.status();
            if ("COMPLETED".equals(request.status()) && treatment.endDate == null) {
                treatment.endDate = LocalDate.now();
            }
            if ("COMPLETED".equals(request.status()) || "CANCELLED".equals(request.status())) {
                List<WithdrawalEntity> withdrawals = withdrawalRepository
                        .findByTenantIdAndTreatmentId(tenantId, treatment.id);
                for (WithdrawalEntity withdrawal : withdrawals) {
                    withdrawal.status = request.status();
                }
                withdrawalRepository.saveAll(withdrawals);
            }
        }
        treatmentRepository.save(treatment);
        auditService.record("TREATMENT_UPDATED", "treatment", treatment.id);
        return toTreatmentResponse(treatment);
    }

    @Transactional
    public HealthDtos.AdministrationResponse addAdministration(UUID treatmentId,
            HealthDtos.AdministrationCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        TreatmentEntity treatment = requireTreatment(treatmentId, tenantId);
        if (!"ACTIVE".equals(treatment.status)) {
            throw new ConflictException("Treatment is not active");
        }

        TreatmentAdministrationEntity administration = new TreatmentAdministrationEntity();
        administration.id = UUID.randomUUID();
        administration.tenantId = tenantId;
        administration.treatmentId = treatment.id;
        administration.administeredAt = request.administeredAt();
        administration.dose = request.dose() == null ? treatment.dose : request.dose();
        administration.actorId = AuthContext.userId();
        administrationRepository.save(administration);
        auditService.record("TREATMENT_ADMINISTERED", "treatment_administration", administration.id);
        return toAdministrationResponse(administration);
    }

    // ------------------------------------------------------------------
    // P7-05 Withdrawals
    // ------------------------------------------------------------------

    public List<HealthDtos.WithdrawalResponse> listWithdrawals(String status) {
        UUID tenantId = TenantContext.requireTenantId();
        List<WithdrawalEntity> entities = status == null || status.isBlank()
                ? withdrawalRepository.findByTenantIdAndStatusOrderByEndDateAsc(tenantId, "ACTIVE")
                : withdrawalRepository.findByTenantIdAndStatusOrderByEndDateAsc(tenantId, status);
        return entities.stream().map(this::toWithdrawalResponse).toList();
    }

    private void createWithdrawals(UUID tenantId, TreatmentEntity treatment, HealthProductEntity product) {
        LocalDate reference = treatment.endDate == null ? treatment.startDate : treatment.endDate;
        List<WithdrawalEntity> withdrawals = new ArrayList<>();
        if (product.withdrawalMeatDays > 0) {
            withdrawals.add(newWithdrawal(tenantId, treatment, "MEAT",
                    reference.plusDays(product.withdrawalMeatDays)));
        }
        if (product.withdrawalEggsDays > 0) {
            withdrawals.add(newWithdrawal(tenantId, treatment, "EGGS",
                    reference.plusDays(product.withdrawalEggsDays)));
        }
        if (product.withdrawalMilkDays > 0) {
            withdrawals.add(newWithdrawal(tenantId, treatment, "MILK",
                    reference.plusDays(product.withdrawalMilkDays)));
        }
        withdrawalRepository.saveAll(withdrawals);
    }

    private WithdrawalEntity newWithdrawal(UUID tenantId, TreatmentEntity treatment, String type,
            LocalDate endDate) {
        WithdrawalEntity withdrawal = new WithdrawalEntity();
        withdrawal.id = UUID.randomUUID();
        withdrawal.tenantId = tenantId;
        withdrawal.treatmentId = treatment.id;
        withdrawal.animalId = treatment.animalId;
        withdrawal.lotId = treatment.lotId;
        withdrawal.type = type;
        withdrawal.startDate = treatment.startDate;
        withdrawal.endDate = endDate;
        withdrawal.status = "ACTIVE";
        return withdrawal;
    }

    // ------------------------------------------------------------------
    // P7-06 Sale blocking (T-03)
    // ------------------------------------------------------------------

    public HealthDtos.SaleEligibilityResponse checkSaleEligibility(UUID animalId, UUID lotId, String type,
            LocalDate date) {
        UUID tenantId = TenantContext.requireTenantId();
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        String effectiveType = type == null || type.isBlank() ? "MEAT" : type;
        List<WithdrawalEntity> active = new ArrayList<>();
        if (animalId != null) {
            active.addAll(withdrawalRepository.findByTenantIdAndAnimalIdAndStatusAndEndDateGreaterThanEqual(
                    tenantId, animalId, "ACTIVE", effectiveDate));
        }
        if (lotId != null) {
            active.addAll(withdrawalRepository.findByTenantIdAndLotIdAndStatusAndEndDateGreaterThanEqual(
                    tenantId, lotId, "ACTIVE", effectiveDate));
        }
        List<WithdrawalEntity> blocking = active.stream()
                .filter(withdrawal -> effectiveType.equals(withdrawal.type))
                .toList();
        return new HealthDtos.SaleEligibilityResponse(blocking.isEmpty(),
                blocking.stream().map(this::toWithdrawalResponse).toList());
    }

    public void assertSaleAllowed(UUID animalId, UUID lotId, String type, LocalDate date) {
        if (!checkSaleEligibility(animalId, lotId, type, date).allowed()) {
            // T-03 : une vente pendant un délai d'attente est un conflit d'état (HTTP 409).
            throw new ConflictException("Animal or lot is under a withdrawal period");
        }
    }

    // ------------------------------------------------------------------
    // P7-07 Mortality
    // ------------------------------------------------------------------

    @Transactional
    public HealthDtos.MortalityResponse recordMortality(HealthDtos.MortalityCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        requireTarget(request.animalId(), request.lotId(), tenantId);
        int count = request.count() == null ? 1 : request.count();

        if (request.animalId() != null) {
            if (count != 1) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "A single mortality record must concern one animal");
            }
            herdLookup.requireAnimal(tenantId, request.animalId());
            animalService.changeStatus(request.animalId(), new HerdDtos.AnimalStatusRequest(
                    "DEAD", "Mortality", request.occurredAt()));
        } else {
            lotService.move(request.lotId(), new LotDtos.LotMovementRequest(
                    "MORTALITY", count, request.occurredAt(), "Mortality"));
        }

        MortalityEntity mortality = new MortalityEntity();
        mortality.id = UUID.randomUUID();
        mortality.tenantId = tenantId;
        mortality.animalId = request.animalId();
        mortality.lotId = request.lotId();
        mortality.count = count;
        mortality.cause = request.cause();
        mortality.occurredAt = request.occurredAt();
        mortality.actorId = AuthContext.userId();
        mortalityRepository.save(mortality);
        auditService.record("MORTALITY_RECORDED", "mortality", mortality.id);
        return toMortalityResponse(mortality);
    }

    public List<HealthDtos.MortalityResponse> listMortalities(UUID animalId) {
        UUID tenantId = TenantContext.requireTenantId();
        List<MortalityEntity> entities = animalId == null
                ? mortalityRepository.findByTenantIdOrderByOccurredAtDesc(tenantId)
                : mortalityRepository.findByTenantIdAndAnimalIdOrderByOccurredAtDesc(tenantId, animalId);
        return entities.stream().map(this::toMortalityResponse).toList();
    }

    // ------------------------------------------------------------------
    // P7-08 Health events
    // ------------------------------------------------------------------

    @Transactional
    public HealthDtos.HealthEventResponse recordHealthEvent(HealthDtos.HealthEventCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        requireTarget(request.animalId(), request.lotId(), tenantId);

        HealthEventEntity event = new HealthEventEntity();
        event.id = UUID.randomUUID();
        event.tenantId = tenantId;
        event.animalId = request.animalId();
        event.lotId = request.lotId();
        event.symptoms = request.symptoms();
        event.affectedCount = request.affectedCount();
        event.diagnosis = request.diagnosis();
        event.severity = request.severity();
        event.occurredAt = request.occurredAt();
        healthEventRepository.save(event);
        auditService.record("HEALTH_EVENT_RECORDED", "health_event", event.id);
        return toHealthEventResponse(event);
    }

    public List<HealthDtos.HealthEventResponse> listHealthEvents(UUID animalId) {
        UUID tenantId = TenantContext.requireTenantId();
        List<HealthEventEntity> entities = animalId == null
                ? healthEventRepository.findByTenantIdOrderByOccurredAtDesc(tenantId)
                : healthEventRepository.findByTenantIdAndAnimalIdOrderByOccurredAtDesc(tenantId, animalId);
        return entities.stream().map(this::toHealthEventResponse).toList();
    }

    // ------------------------------------------------------------------
    // P7-09 Quarantine
    // ------------------------------------------------------------------

    public List<HealthDtos.QuarantineResponse> listQuarantines(String status) {
        UUID tenantId = TenantContext.requireTenantId();
        List<QuarantineEntity> entities = status == null || status.isBlank()
                ? quarantineRepository.findByTenantIdOrderByStartDateDesc(tenantId)
                : quarantineRepository.findByTenantIdAndStatusOrderByStartDateDesc(tenantId, status);
        return entities.stream().map(this::toQuarantineResponse).toList();
    }

    @Transactional
    public HealthDtos.QuarantineResponse createQuarantine(HealthDtos.QuarantineCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        requireTarget(request.animalId(), request.lotId(), tenantId);

        QuarantineEntity quarantine = new QuarantineEntity();
        quarantine.id = UUID.randomUUID();
        quarantine.tenantId = tenantId;
        quarantine.animalId = request.animalId();
        quarantine.lotId = request.lotId();
        quarantine.startDate = request.startDate();
        quarantine.endDate = request.endDate();
        quarantine.reason = request.reason();
        quarantine.status = "ACTIVE";
        quarantineRepository.save(quarantine);

        if (request.animalId() != null) {
            animalService.changeStatus(request.animalId(), new HerdDtos.AnimalStatusRequest(
                    "QUARANTINED", request.reason(), startOfDay(request.startDate())));
        }
        auditService.record("QUARANTINE_STARTED", "quarantine", quarantine.id);
        return toQuarantineResponse(quarantine);
    }

    @Transactional
    public HealthDtos.QuarantineResponse releaseQuarantine(UUID quarantineId) {
        UUID tenantId = TenantContext.requireTenantId();
        QuarantineEntity quarantine = quarantineRepository.findByIdAndTenantId(quarantineId, tenantId)
                .orElseThrow(() -> new NotFoundException("Quarantine not found"));
        if ("RELEASED".equals(quarantine.status)) {
            throw new ConflictException("Quarantine is already released");
        }
        quarantine.status = "RELEASED";
        if (quarantine.endDate == null) {
            quarantine.endDate = LocalDate.now();
        }
        quarantine.releasedBy = AuthContext.userId();
        quarantineRepository.save(quarantine);

        if (quarantine.animalId != null) {
            animalService.changeStatus(quarantine.animalId, new HerdDtos.AnimalStatusRequest(
                    "ACTIVE", "Quarantine released", Instant.now()));
        }
        auditService.record("QUARANTINE_RELEASED", "quarantine", quarantine.id);
        return toQuarantineResponse(quarantine);
    }

    // ------------------------------------------------------------------
    // P7-10 Health book
    // ------------------------------------------------------------------

    public HealthDtos.HealthBookResponse healthBook(UUID animalId) {
        UUID tenantId = TenantContext.requireTenantId();
        herdLookup.requireAnimal(tenantId, animalId);
        List<HealthDtos.VaccinationResponse> vaccinations = vaccinationRepository
                .findByTenantIdAndAnimalIdOrderByPerformedAtDesc(tenantId, animalId).stream()
                .map(this::toVaccinationResponse).toList();
        List<HealthDtos.TreatmentResponse> treatments = treatmentRepository
                .findByTenantIdAndAnimalIdOrderByStartDateDesc(tenantId, animalId).stream()
                .map(this::toTreatmentResponse).toList();
        List<HealthDtos.WithdrawalResponse> withdrawals = withdrawalRepository
                .findByTenantIdAndAnimalIdOrderByEndDateDesc(tenantId, animalId).stream()
                .map(this::toWithdrawalResponse).toList();
        List<HealthDtos.MortalityResponse> mortalities = mortalityRepository
                .findByTenantIdAndAnimalIdOrderByOccurredAtDesc(tenantId, animalId).stream()
                .map(this::toMortalityResponse).toList();
        List<HealthDtos.QuarantineResponse> quarantines = quarantineRepository
                .findByTenantIdAndAnimalIdOrderByStartDateDesc(tenantId, animalId).stream()
                .map(this::toQuarantineResponse).toList();
        return new HealthDtos.HealthBookResponse(animalId, vaccinations, treatments, withdrawals,
                mortalities, quarantines);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void apply(HealthProductEntity product, HealthDtos.ProductCreateRequest request) {
        product.name = request.name();
        product.type = request.type();
        product.speciesAllowed = request.speciesAllowed() == null
                ? new ArrayList<>() : new ArrayList<>(request.speciesAllowed());
        product.dose = request.dose();
        product.route = request.route();
        product.withdrawalMeatDays = request.withdrawalMeatDays() == null ? 0 : request.withdrawalMeatDays();
        product.withdrawalEggsDays = request.withdrawalEggsDays() == null ? 0 : request.withdrawalEggsDays();
        product.withdrawalMilkDays = request.withdrawalMilkDays() == null ? 0 : request.withdrawalMilkDays();
        product.shelfLifeDays = request.shelfLifeDays();
        product.supplier = request.supplier();
        product.stockQty = request.stockQty() == null ? BigDecimal.ZERO : request.stockQty();
    }

    private void requireTarget(UUID animalId, UUID lotId, UUID tenantId) {
        if (animalId == null && lotId == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "An animal or a lot is required");
        }
        if (animalId != null) {
            herdLookup.requireAnimal(tenantId, animalId);
        }
        if (lotId != null) {
            lotService.get(lotId);
        }
    }

    private HealthProductEntity requireProduct(UUID productId, UUID tenantId) {
        return productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new NotFoundException("Health product not found"));
    }

    private TreatmentEntity requireTreatment(UUID treatmentId, UUID tenantId) {
        return treatmentRepository.findByIdAndTenantId(treatmentId, tenantId)
                .orElseThrow(() -> new NotFoundException("Treatment not found"));
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private HealthDtos.ProductResponse toProductResponse(HealthProductEntity product) {
        return new HealthDtos.ProductResponse(product.id, product.name, product.type,
                product.speciesAllowed == null ? List.of() : List.copyOf(product.speciesAllowed),
                product.dose, product.route, product.withdrawalMeatDays, product.withdrawalEggsDays,
                product.withdrawalMilkDays, product.shelfLifeDays, product.supplier, product.stockQty,
                product.archivedAt);
    }

    private HealthDtos.VaccinationResponse toVaccinationResponse(VaccinationEntity vaccination) {
        return new HealthDtos.VaccinationResponse(vaccination.id, vaccination.animalId, vaccination.lotId,
                vaccination.productId, vaccination.planId, vaccination.vaccineLot, vaccination.dose,
                vaccination.route, vaccination.count, vaccination.performedAt, vaccination.vetId,
                vaccination.notes);
    }

    private HealthDtos.TreatmentResponse toTreatmentResponse(TreatmentEntity treatment) {
        UUID tenantId = TenantContext.requireTenantId();
        List<WithdrawalEntity> withdrawals = withdrawalRepository
                .findByTenantIdAndTreatmentId(tenantId, treatment.id);
        LocalDate withdrawalEndDate = withdrawals.stream()
                .map(withdrawal -> withdrawal.endDate)
                .filter(endDate -> endDate != null)
                .max(LocalDate::compareTo)
                .orElse(null);
        List<HealthDtos.AdministrationResponse> administrations = administrationRepository
                .findByTenantIdAndTreatmentIdOrderByAdministeredAtDesc(tenantId, treatment.id).stream()
                .map(this::toAdministrationResponse).toList();
        return new HealthDtos.TreatmentResponse(treatment.id, treatment.animalId, treatment.lotId,
                treatment.productId, treatment.diagnosis, treatment.symptoms, treatment.dose,
                treatment.route, treatment.startDate, treatment.durationDays, treatment.endDate,
                treatment.status, treatment.notes, withdrawalEndDate, administrations);
    }

    private HealthDtos.AdministrationResponse toAdministrationResponse(
            TreatmentAdministrationEntity administration) {
        return new HealthDtos.AdministrationResponse(administration.id, administration.treatmentId,
                administration.administeredAt, administration.dose, administration.actorId);
    }

    private HealthDtos.WithdrawalResponse toWithdrawalResponse(WithdrawalEntity withdrawal) {
        return new HealthDtos.WithdrawalResponse(withdrawal.id, withdrawal.treatmentId,
                withdrawal.animalId, withdrawal.lotId, withdrawal.type, withdrawal.startDate,
                withdrawal.endDate, withdrawal.status);
    }

    private HealthDtos.HealthEventResponse toHealthEventResponse(HealthEventEntity event) {
        return new HealthDtos.HealthEventResponse(event.id, event.animalId, event.lotId, event.symptoms,
                event.affectedCount, event.diagnosis, event.severity, event.occurredAt);
    }

    private HealthDtos.MortalityResponse toMortalityResponse(MortalityEntity mortality) {
        return new HealthDtos.MortalityResponse(mortality.id, mortality.animalId, mortality.lotId,
                mortality.count, mortality.cause, mortality.occurredAt, mortality.actorId);
    }

    private HealthDtos.QuarantineResponse toQuarantineResponse(QuarantineEntity quarantine) {
        return new HealthDtos.QuarantineResponse(quarantine.id, quarantine.animalId, quarantine.lotId,
                quarantine.startDate, quarantine.endDate, quarantine.reason, quarantine.status,
                quarantine.releasedBy);
    }
}
