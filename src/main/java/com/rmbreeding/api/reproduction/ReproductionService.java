package com.rmbreeding.api.reproduction;

import com.rmbreeding.api.herd.HerdLookup;
import com.rmbreeding.api.herd.LotDtos;
import com.rmbreeding.api.herd.LotService;
import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.identity.AuthContext;
import com.rmbreeding.api.referential.SpeciesLookup;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.Cursor;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.PageResponse;
import com.rmbreeding.api.shared.TenantContext;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReproductionService {

    private static final Set<String> FINAL_ANIMAL_STATUSES =
            Set.of("SOLD", "DEAD", "SLAUGHTERED", "TRANSFERRED", "ARCHIVED");
    private static final Set<String> ACTIVE_CYCLE_STATUSES = Set.of("OPEN", "MATED", "PREGNANT");
    private static final Set<String> CLOSED_CYCLE_STATUSES = Set.of("ABORTED", "BIRTHED");
    private static final int DEFAULT_DIAGNOSIS_DAYS = 35;
    private static final int UNCERTAIN_DIAGNOSIS_DAYS = 14;

    private final ReproductionCycleRepository cycleRepository;
    private final HeatRepository heatRepository;
    private final MatingRepository matingRepository;
    private final PregnancyDiagnosisRepository diagnosisRepository;
    private final AbortionRepository abortionRepository;
    private final BirthRepository birthRepository;
    private final LitterRepository litterRepository;
    private final WeaningRepository weaningRepository;
    private final HerdLookup herdLookup;
    private final LotService lotService;
    private final SpeciesLookup speciesLookup;
    private final AuditService auditService;

    public ReproductionService(ReproductionCycleRepository cycleRepository,
            HeatRepository heatRepository,
            MatingRepository matingRepository,
            PregnancyDiagnosisRepository diagnosisRepository,
            AbortionRepository abortionRepository,
            BirthRepository birthRepository,
            LitterRepository litterRepository,
            WeaningRepository weaningRepository,
            HerdLookup herdLookup,
            LotService lotService,
            SpeciesLookup speciesLookup,
            AuditService auditService) {
        this.cycleRepository = cycleRepository;
        this.heatRepository = heatRepository;
        this.matingRepository = matingRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.abortionRepository = abortionRepository;
        this.birthRepository = birthRepository;
        this.litterRepository = litterRepository;
        this.weaningRepository = weaningRepository;
        this.herdLookup = herdLookup;
        this.lotService = lotService;
        this.speciesLookup = speciesLookup;
        this.auditService = auditService;
    }

    public long countBirths(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.requireTenantId();
        return birthRepository.countByTenantIdAndOccurredAtBetween(tenantId,
                from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.atTime(23, 59, 59).atOffset(ZoneOffset.UTC).toInstant());
    }

    public long countMatings(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.requireTenantId();
        return matingRepository.countByTenantIdAndMatedAtBetween(tenantId,
                from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.atTime(23, 59, 59).atOffset(ZoneOffset.UTC).toInstant());
    }

    public long countPositiveDiagnoses(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.requireTenantId();
        return diagnosisRepository.countByTenantIdAndResultAndDiagnosedAtBetween(tenantId, "POSITIVE",
                from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.atTime(23, 59, 59).atOffset(ZoneOffset.UTC).toInstant());
    }

    public long countAbortions(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.requireTenantId();
        return abortionRepository.countByTenantIdAndOccurredAtBetween(tenantId,
                from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.atTime(23, 59, 59).atOffset(ZoneOffset.UTC).toInstant());
    }

    public PageResponse<ReproductionDtos.CycleResponse> listCycles(UUID femaleId, String status,
            Integer limit, String cursor) {
        UUID tenantId = TenantContext.requireTenantId();
        int size = limit == null ? 50 : Math.max(1, Math.min(limit, 100));
        Cursor decoded = Cursor.decode(cursor);

        Specification<ReproductionCycleEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("tenantId"), tenantId));
            if (femaleId != null) {
                predicates.add(builder.equal(root.get("femaleId"), femaleId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (decoded != null) {
                Predicate older = builder.lessThan(root.<Instant>get("createdAt"), decoded.createdAt());
                Predicate sameInstantOlderId = builder.and(
                        builder.equal(root.<Instant>get("createdAt"), decoded.createdAt()),
                        builder.lessThan(root.<UUID>get("id"), decoded.id()));
                predicates.add(builder.or(older, sameInstantOlderId));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };

        Page<ReproductionCycleEntity> page = cycleRepository.findAll(specification,
                PageRequest.of(0, size + 1, Sort.by(
                        Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        List<ReproductionCycleEntity> entities = new ArrayList<>(page.getContent());
        boolean hasMore = entities.size() > size;
        if (hasMore) {
            entities = new ArrayList<>(entities.subList(0, size));
        }
        String nextCursor = null;
        if (hasMore && !entities.isEmpty()) {
            ReproductionCycleEntity last = entities.getLast();
            nextCursor = new Cursor(last.createdAt, last.id).encode();
        }
        return new PageResponse<>(entities.stream().map(this::toCycleResponse).toList(),
                nextCursor, hasMore);
    }

    public ReproductionDtos.CycleDetailResponse getCycle(UUID cycleId) {
        UUID tenantId = TenantContext.requireTenantId();
        ReproductionCycleEntity cycle = requireCycle(cycleId, tenantId);
        List<BirthEntity> births = birthRepository
                .findByTenantIdAndCycleIdOrderByOccurredAtDesc(tenantId, cycleId);
        List<LitterEntity> litters = new ArrayList<>();
        for (BirthEntity birth : births) {
            litters.addAll(litterRepository.findByTenantIdAndBirthIdOrderByBornAliveDesc(
                    tenantId, birth.id));
        }
        return new ReproductionDtos.CycleDetailResponse(
                toCycleResponse(cycle),
                heatRepository.findByTenantIdAndCycleIdOrderByObservedAtDesc(tenantId, cycleId)
                        .stream().map(this::toHeatResponse).toList(),
                matingRepository.findByTenantIdAndCycleIdOrderByMatedAtDesc(tenantId, cycleId)
                        .stream().map(mating -> toMatingResponse(mating,
                                speciesOfCycle(cycle, tenantId))).toList(),
                diagnosisRepository.findByTenantIdAndCycleIdOrderByDiagnosedAtDesc(tenantId, cycleId)
                        .stream().map(this::toDiagnosisResponse).toList(),
                abortionRepository.findByTenantIdAndCycleIdOrderByOccurredAtDesc(tenantId, cycleId)
                        .stream().map(this::toAbortionResponse).toList(),
                births.stream().map(birth -> toBirthResponse(birth,
                        litters.stream()
                                .filter(litter -> litter.birthId.equals(birth.id))
                                .map(litter -> litter.id)
                                .findFirst()
                                .orElse(null))).toList(),
                litters.stream().map(this::toLitterResponse).toList());
    }

    @Transactional
    public ReproductionDtos.CycleResponse createCycle(ReproductionDtos.CycleCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        HerdLookup.AnimalInfo female = requireFemaleAnimal(request.femaleId(), tenantId);
        if (cycleRepository.findFirstByTenantIdAndFemaleIdAndStatusInOrderByCreatedAtDesc(
                tenantId, female.id(), ACTIVE_CYCLE_STATUSES).isPresent()) {
            throw new ConflictException("An active reproduction cycle already exists for this female");
        }
        LocalDate startDate = request.startDate() == null ? LocalDate.now() : request.startDate();
        ReproductionCycleEntity cycle = newCycle(tenantId, female, startDate);
        cycleRepository.save(cycle);
        auditService.record("REPRODUCTION_CYCLE_CREATED", "reproduction_cycle", cycle.id);
        return toCycleResponse(cycle);
    }

    @Transactional
    public ReproductionDtos.HeatResponse recordHeat(ReproductionDtos.HeatCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        HerdLookup.AnimalInfo animal = requireFemaleAnimal(request.animalId(), tenantId);
        LocalDate observedDate = dateOf(request.observedAt());
        ReproductionCycleEntity cycle = findOrCreateCycle(tenantId, animal, observedDate);
        if ("PREGNANT".equals(cycle.status)) {
            throw new ConflictException("Female is pregnant");
        }
        SpeciesLookup.SpeciesInfo species = species(animal.speciesId(), tenantId);

        HeatEntity heat = new HeatEntity();
        heat.id = UUID.randomUUID();
        heat.tenantId = tenantId;
        heat.cycleId = cycle.id;
        heat.animalId = animal.id();
        heat.observedAt = request.observedAt();
        heat.intensity = request.intensity();
        heat.nextExpectedAt = addDays(observedDate, species.heatCycleDays());
        heat.notes = request.notes();
        heatRepository.save(heat);
        auditService.record("HEAT_RECORDED", "heat", heat.id);
        return toHeatResponse(heat);
    }

    @Transactional
    public ReproductionDtos.MatingResponse recordMating(ReproductionDtos.MatingCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        HerdLookup.AnimalInfo female = requireFemaleAnimal(request.femaleId(), tenantId);
        if (request.maleId() != null) {
            validateMale(request.maleId(), female.farmId(), tenantId);
        }
        if (cycleRepository.existsByTenantIdAndFemaleIdAndStatus(tenantId, female.id(), "PREGNANT")) {
            throw new ConflictException("Female is already pregnant");
        }
        LocalDate matedDate = dateOf(request.matedAt());
        ReproductionCycleEntity cycle = findOrCreateCycle(tenantId, female, matedDate);
        if ("PREGNANT".equals(cycle.status)) {
            throw new ConflictException("Female is already pregnant");
        }

        MatingEntity mating = new MatingEntity();
        mating.id = UUID.randomUUID();
        mating.tenantId = tenantId;
        mating.cycleId = cycle.id;
        mating.femaleId = female.id();
        mating.maleId = request.maleId();
        mating.semenCode = request.semenCode();
        mating.method = request.method();
        mating.matedAt = request.matedAt();
        mating.operatorId = AuthContext.userId();
        mating.notes = request.notes();
        matingRepository.save(mating);

        cycle.status = "MATED";
        cycle.updatedAt = Instant.now();
        cycleRepository.save(cycle);
        auditService.record("MATING_RECORDED", "mating", mating.id);
        return toMatingResponse(mating, species(female.speciesId(), tenantId));
    }

    @Transactional
    public ReproductionDtos.DiagnosisResponse recordDiagnosis(
            ReproductionDtos.DiagnosisCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        ReproductionCycleEntity cycle = requireCycle(request.cycleId(), tenantId);
        if (CLOSED_CYCLE_STATUSES.contains(cycle.status)) {
            throw new ConflictException("Cycle is closed");
        }
        if (!matingRepository.existsByTenantIdAndCycleId(tenantId, cycle.id)) {
            throw new ConflictException("Cycle has no mating");
        }
        SpeciesLookup.SpeciesInfo species = speciesOfCycle(cycle, tenantId);
        LocalDate diagnosedDate = dateOf(request.diagnosedAt());

        PregnancyDiagnosisEntity diagnosis = new PregnancyDiagnosisEntity();
        diagnosis.id = UUID.randomUUID();
        diagnosis.tenantId = tenantId;
        diagnosis.cycleId = cycle.id;
        diagnosis.diagnosedAt = request.diagnosedAt();
        diagnosis.result = request.result();
        diagnosis.notes = request.notes();
        switch (request.result()) {
            case "POSITIVE" -> {
                cycle.status = "PREGNANT";
                diagnosis.nextDiagnosisAt = null;
            }
            case "NEGATIVE" -> {
                cycle.status = "OPEN";
                diagnosis.nextDiagnosisAt = addDays(diagnosedDate, species.heatCycleDays());
            }
            default -> diagnosis.nextDiagnosisAt = addDays(diagnosedDate, UNCERTAIN_DIAGNOSIS_DAYS);
        }
        diagnosisRepository.save(diagnosis);

        cycle.updatedAt = Instant.now();
        cycleRepository.save(cycle);
        auditService.record("PREGNANCY_DIAGNOSED", "pregnancy_diagnosis", diagnosis.id);
        return toDiagnosisResponse(diagnosis);
    }

    @Transactional
    public ReproductionDtos.AbortionResponse recordAbortion(
            ReproductionDtos.AbortionCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        ReproductionCycleEntity cycle = requireCycle(request.cycleId(), tenantId);
        if (!"PREGNANT".equals(cycle.status)) {
            throw new ConflictException("Cycle is not pregnant");
        }

        AbortionEntity abortion = new AbortionEntity();
        abortion.id = UUID.randomUUID();
        abortion.tenantId = tenantId;
        abortion.cycleId = cycle.id;
        abortion.occurredAt = request.occurredAt();
        abortion.stage = request.stage();
        abortion.suspectedCause = request.suspectedCause();
        abortionRepository.save(abortion);

        cycle.status = "ABORTED";
        cycle.endDate = dateOf(request.occurredAt());
        cycle.updatedAt = Instant.now();
        cycleRepository.save(cycle);
        auditService.record("ABORTION_RECORDED", "abortion", abortion.id);
        return toAbortionResponse(abortion);
    }

    @Transactional
    public ReproductionDtos.BirthCreationResponse recordBirth(ReproductionDtos.BirthCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        ReproductionCycleEntity cycle = requireCycle(request.cycleId(), tenantId);
        if (CLOSED_CYCLE_STATUSES.contains(cycle.status)) {
            throw new ConflictException("Cycle is closed");
        }
        HerdLookup.AnimalInfo female = requireFemaleAnimal(request.femaleId(), tenantId);
        if (!female.id().equals(cycle.femaleId)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Female does not match the reproduction cycle");
        }
        SpeciesLookup.SpeciesInfo species = species(female.speciesId(), tenantId);
        List<MatingEntity> matings = matingRepository
                .findByTenantIdAndCycleIdOrderByMatedAtDesc(tenantId, cycle.id);
        boolean retroactive = matings.isEmpty();
        LocalDate occurredDate = dateOf(request.occurredAt());

        BirthEntity birth = new BirthEntity();
        birth.id = UUID.randomUUID();
        birth.tenantId = tenantId;
        birth.cycleId = cycle.id;
        birth.femaleId = female.id();
        birth.occurredAt = request.occurredAt();
        birth.bornAlive = request.bornAlive();
        birth.stillborn = request.stillborn() == null ? 0 : request.stillborn();
        birth.mummified = request.mummified() == null ? 0 : request.mummified();
        birth.litterWeight = request.litterWeight();
        birth.assistance = Boolean.TRUE.equals(request.assistance());
        birth.complications = request.complications();
        birth.operatorId = AuthContext.userId();
        birthRepository.save(birth);

        LitterEntity litter = new LitterEntity();
        litter.id = UUID.randomUUID();
        litter.tenantId = tenantId;
        litter.birthId = birth.id;
        litter.name = "Portée " + occurredDate;
        litter.bornAlive = birth.bornAlive;
        litter.weaningDate = addDays(occurredDate, species.weaningAgeDays());
        litterRepository.save(litter);

        cycle.status = "BIRTHED";
        cycle.endDate = occurredDate;
        cycle.parity += 1;
        cycle.updatedAt = Instant.now();
        cycleRepository.save(cycle);

        auditService.record("FARROWING_RECORDED", "birth", birth.id);
        LocalDate suggestedMatingDate = retroactive
                ? addDays(occurredDate, species.gestationDays() == null ? null : -species.gestationDays())
                : null;
        return new ReproductionDtos.BirthCreationResponse(
                toBirthResponse(birth, litter.id), litter.id, retroactive, suggestedMatingDate);
    }

    @Transactional
    public ReproductionDtos.WeaningResponse recordWeaning(
            ReproductionDtos.WeaningCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        LitterEntity litter = requireLitter(request.litterId(), tenantId);
        if (litter.weanedCount != null) {
            throw new ConflictException("Litter is already weaned");
        }
        if (request.count() > litter.bornAlive) {
            throw new ConflictException("Weaned count exceeds born alive");
        }

        WeaningEntity weaning = new WeaningEntity();
        weaning.id = UUID.randomUUID();
        weaning.tenantId = tenantId;
        weaning.litterId = litter.id;
        weaning.weanedAt = request.weanedAt();
        weaning.count = request.count();
        weaning.averageWeight = request.averageWeight();
        weaningRepository.save(weaning);

        litter.weanedCount = request.count();
        litter.weaningDate = dateOf(request.weanedAt());
        litterRepository.save(litter);
        auditService.record("WEANING_RECORDED", "weaning", weaning.id);
        return toWeaningResponse(weaning);
    }

    @Transactional
    public LotDtos.LotResponse createLotFromLitter(UUID litterId,
            ReproductionDtos.LitterLotCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        LitterEntity litter = requireLitter(litterId, tenantId);
        if (litter.lotId != null) {
            throw new ConflictException("Litter is already linked to a lot");
        }
        BirthEntity birth = birthRepository.findByIdAndTenantId(litter.birthId, tenantId)
                .orElseThrow(() -> new NotFoundException("Birth not found"));
        ReproductionCycleEntity cycle = requireCycle(birth.cycleId, tenantId);
        HerdLookup.AnimalInfo female = herdLookup.requireAnimal(tenantId, cycle.femaleId);
        int count = litter.weanedCount == null ? litter.bornAlive : litter.weanedCount;
        if (count <= 0) {
            throw new ConflictException("Litter has no animal to create a lot");
        }

        LotDtos.LotResponse lot = lotService.create(new LotDtos.LotCreateRequest(
                female.farmId(), female.speciesId(), request.name(), request.code(),
                request.productionType(), LocalDate.now(), count, request.unitCost()));
        litter.lotId = lot.id();
        litterRepository.save(litter);
        auditService.record("LITTER_LOT_CREATED", "litter", litter.id);
        return lot;
    }

    private ReproductionCycleEntity findOrCreateCycle(UUID tenantId, HerdLookup.AnimalInfo female,
            LocalDate startDate) {
        return cycleRepository.findFirstByTenantIdAndFemaleIdAndStatusInOrderByCreatedAtDesc(
                        tenantId, female.id(), ACTIVE_CYCLE_STATUSES)
                .orElseGet(() -> {
                    ReproductionCycleEntity cycle = newCycle(tenantId, female, startDate);
                    cycleRepository.save(cycle);
                    return cycle;
                });
    }

    private ReproductionCycleEntity newCycle(UUID tenantId, HerdLookup.AnimalInfo female,
            LocalDate startDate) {
        Instant now = Instant.now();
        ReproductionCycleEntity cycle = new ReproductionCycleEntity();
        cycle.id = UUID.randomUUID();
        cycle.tenantId = tenantId;
        cycle.farmId = female.farmId();
        cycle.femaleId = female.id();
        cycle.status = "OPEN";
        cycle.parity = 0;
        cycle.startDate = startDate;
        cycle.createdAt = now;
        cycle.updatedAt = now;
        return cycle;
    }

    private ReproductionCycleEntity requireCycle(UUID cycleId, UUID tenantId) {
        return cycleRepository.findByIdAndTenantId(cycleId, tenantId)
                .orElseThrow(() -> new NotFoundException("Reproduction cycle not found"));
    }

    private LitterEntity requireLitter(UUID litterId, UUID tenantId) {
        return litterRepository.findByIdAndTenantId(litterId, tenantId)
                .orElseThrow(() -> new NotFoundException("Litter not found"));
    }

    private HerdLookup.AnimalInfo requireFemaleAnimal(UUID animalId, UUID tenantId) {
        HerdLookup.AnimalInfo animal = herdLookup.requireAnimal(tenantId, animalId);
        if (!"FEMALE".equals(animal.sex())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Animal is not female");
        }
        if (FINAL_ANIMAL_STATUSES.contains(animal.status())) {
            throw new ConflictException("Animal is in a final state");
        }
        return animal;
    }

    private void validateMale(UUID maleId, UUID farmId, UUID tenantId) {
        HerdLookup.AnimalInfo male = herdLookup.requireAnimal(tenantId, maleId);
        if (!"MALE".equals(male.sex())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Male animal is not male");
        }
        if (!male.farmId().equals(farmId)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Male must belong to the same farm");
        }
        if (FINAL_ANIMAL_STATUSES.contains(male.status())) {
            throw new ConflictException("Male animal is in a final state");
        }
    }

    private SpeciesLookup.SpeciesInfo species(UUID speciesId, UUID tenantId) {
        return speciesLookup.requireSpecies(tenantId, speciesId);
    }

    private SpeciesLookup.SpeciesInfo speciesOfCycle(ReproductionCycleEntity cycle, UUID tenantId) {
        HerdLookup.AnimalInfo female = herdLookup.requireAnimal(tenantId, cycle.femaleId);
        return species(female.speciesId(), tenantId);
    }

    private int diagnosisDays(SpeciesLookup.SpeciesInfo species) {
        Map<String, Object> settings = species.settings();
        if (settings != null && settings.get("diagnosisDays") instanceof Number days) {
            return days.intValue();
        }
        return DEFAULT_DIAGNOSIS_DAYS;
    }

    private LocalDate addDays(LocalDate date, Integer days) {
        if (date == null || days == null) {
            return null;
        }
        return date.plusDays(days);
    }

    private LocalDate dateOf(Instant instant) {
        return instant == null ? null : instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private ReproductionDtos.CycleResponse toCycleResponse(ReproductionCycleEntity cycle) {
        UUID tenantId = TenantContext.requireTenantId();
        MatingEntity latest = matingRepository
                .findFirstByTenantIdAndCycleIdOrderByMatedAtDesc(tenantId, cycle.id)
                .orElse(null);
        LocalDate expectedBirthDate = null;
        LocalDate expectedDiagnosisDate = null;
        if (latest != null) {
            SpeciesLookup.SpeciesInfo species = speciesOfCycle(cycle, tenantId);
            expectedBirthDate = addDays(dateOf(latest.matedAt), species.gestationDays());
            expectedDiagnosisDate = addDays(dateOf(latest.matedAt), diagnosisDays(species));
        }
        return new ReproductionDtos.CycleResponse(cycle.id, cycle.farmId, cycle.femaleId,
                cycle.status, cycle.parity, cycle.startDate, cycle.endDate,
                expectedBirthDate, expectedDiagnosisDate, cycle.createdAt, cycle.updatedAt);
    }

    private ReproductionDtos.HeatResponse toHeatResponse(HeatEntity heat) {
        return new ReproductionDtos.HeatResponse(heat.id, heat.cycleId, heat.animalId,
                heat.observedAt, heat.intensity, heat.nextExpectedAt, heat.notes);
    }

    private ReproductionDtos.MatingResponse toMatingResponse(MatingEntity mating,
            SpeciesLookup.SpeciesInfo species) {
        return new ReproductionDtos.MatingResponse(mating.id, mating.cycleId, mating.femaleId,
                mating.maleId, mating.semenCode, mating.method, mating.matedAt,
                addDays(dateOf(mating.matedAt), species.gestationDays()),
                addDays(dateOf(mating.matedAt), diagnosisDays(species)), mating.notes);
    }

    private ReproductionDtos.DiagnosisResponse toDiagnosisResponse(
            PregnancyDiagnosisEntity diagnosis) {
        return new ReproductionDtos.DiagnosisResponse(diagnosis.id, diagnosis.cycleId,
                diagnosis.diagnosedAt, diagnosis.result, diagnosis.nextDiagnosisAt,
                diagnosis.notes);
    }

    private ReproductionDtos.AbortionResponse toAbortionResponse(AbortionEntity abortion) {
        return new ReproductionDtos.AbortionResponse(abortion.id, abortion.cycleId,
                abortion.occurredAt, abortion.stage, abortion.suspectedCause);
    }

    private ReproductionDtos.BirthResponse toBirthResponse(BirthEntity birth, UUID litterId) {
        return new ReproductionDtos.BirthResponse(birth.id, birth.cycleId, birth.femaleId,
                birth.occurredAt, birth.bornAlive, birth.stillborn, birth.mummified,
                birth.litterWeight, birth.assistance, birth.complications, litterId);
    }

    private ReproductionDtos.LitterResponse toLitterResponse(LitterEntity litter) {
        UUID tenantId = TenantContext.requireTenantId();
        List<ReproductionDtos.WeaningResponse> weanings = weaningRepository
                .findByTenantIdAndLitterIdOrderByWeanedAtDesc(tenantId, litter.id)
                .stream().map(this::toWeaningResponse).toList();
        return new ReproductionDtos.LitterResponse(litter.id, litter.birthId, litter.lotId,
                litter.name, litter.bornAlive, litter.weanedCount, litter.weaningDate, weanings);
    }

    private ReproductionDtos.WeaningResponse toWeaningResponse(WeaningEntity weaning) {
        return new ReproductionDtos.WeaningResponse(weaning.id, weaning.litterId,
                weaning.weanedAt, weaning.count, weaning.averageWeight);
    }
}
