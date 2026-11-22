package com.rmbreeding.api.reproduction;

import com.rmbreeding.api.herd.HerdLookup;
import com.rmbreeding.api.herd.LotDtos;
import com.rmbreeding.api.herd.LotService;
import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.referential.SpeciesLookup;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ConflictException;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.PageResponse;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReproductionServiceTest {

    @Mock
    private ReproductionCycleRepository cycleRepository;
    @Mock
    private HeatRepository heatRepository;
    @Mock
    private MatingRepository matingRepository;
    @Mock
    private PregnancyDiagnosisRepository diagnosisRepository;
    @Mock
    private AbortionRepository abortionRepository;
    @Mock
    private BirthRepository birthRepository;
    @Mock
    private LitterRepository litterRepository;
    @Mock
    private WeaningRepository weaningRepository;
    @Mock
    private HerdLookup herdLookup;
    @Mock
    private LotService lotService;
    @Mock
    private SpeciesLookup speciesLookup;
    @Mock
    private AuditService auditService;

    private ReproductionService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID farmId = UUID.randomUUID();
    private final UUID speciesId = UUID.randomUUID();
    private final UUID femaleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReproductionService(cycleRepository, heatRepository, matingRepository,
                diagnosisRepository, abortionRepository, birthRepository, litterRepository,
                weaningRepository, herdLookup, lotService, speciesLookup, auditService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createsOpenCycleForFemale() {
        when(herdLookup.requireAnimal(tenantId, femaleId)).thenReturn(female(femaleId));
        when(cycleRepository.findFirstByTenantIdAndFemaleIdAndStatusInOrderByCreatedAtDesc(
                any(), any(), any())).thenReturn(Optional.empty());

        ReproductionDtos.CycleResponse response = service.createCycle(
                new ReproductionDtos.CycleCreateRequest(femaleId, LocalDate.of(2026, 3, 1)));

        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.parity()).isZero();
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        verify(cycleRepository).save(any(ReproductionCycleEntity.class));
        verify(auditService).record("REPRODUCTION_CYCLE_CREATED", "reproduction_cycle", response.id());
    }

    @Test
    void rejectsCycleForNonFemale() {
        when(herdLookup.requireAnimal(tenantId, femaleId)).thenReturn(male(femaleId));

        assertThatThrownBy(() -> service.createCycle(
                new ReproductionDtos.CycleCreateRequest(femaleId, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("female");
    }

    @Test
    void rejectsDuplicateActiveCycle() {
        when(herdLookup.requireAnimal(tenantId, femaleId)).thenReturn(female(femaleId));
        when(cycleRepository.findFirstByTenantIdAndFemaleIdAndStatusInOrderByCreatedAtDesc(
                any(), any(), any())).thenReturn(Optional.of(cycle(UUID.randomUUID(), "OPEN")));

        assertThatThrownBy(() -> service.createCycle(
                new ReproductionDtos.CycleCreateRequest(femaleId, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("active");
    }

    @Test
    void recordsHeatAndComputesNextExpectedDate() {
        when(herdLookup.requireAnimal(tenantId, femaleId)).thenReturn(female(femaleId));
        when(cycleRepository.findFirstByTenantIdAndFemaleIdAndStatusInOrderByCreatedAtDesc(
                any(), any(), any())).thenReturn(Optional.of(cycle(UUID.randomUUID(), "OPEN")));
        when(speciesLookup.requireSpecies(tenantId, speciesId)).thenReturn(species());

        ReproductionDtos.HeatResponse response = service.recordHeat(
                new ReproductionDtos.HeatCreateRequest(femaleId,
                        Instant.parse("2026-03-01T10:00:00Z"), "HIGH", "Chaleur franche"));

        assertThat(response.nextExpectedAt()).isEqualTo(LocalDate.of(2026, 3, 22));
        assertThat(response.intensity()).isEqualTo("HIGH");
        verify(heatRepository).save(any(HeatEntity.class));
        verify(auditService).record("HEAT_RECORDED", "heat", response.id());
    }

    @Test
    void rejectsHeatForPregnantFemale() {
        when(herdLookup.requireAnimal(tenantId, femaleId)).thenReturn(female(femaleId));
        when(cycleRepository.findFirstByTenantIdAndFemaleIdAndStatusInOrderByCreatedAtDesc(
                any(), any(), any())).thenReturn(Optional.of(cycle(UUID.randomUUID(), "PREGNANT")));

        assertThatThrownBy(() -> service.recordHeat(new ReproductionDtos.HeatCreateRequest(
                femaleId, Instant.parse("2026-03-01T10:00:00Z"), null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("pregnant");
    }

    @Test
    void recordsMatingAndComputesExpectedDates() {
        when(herdLookup.requireAnimal(tenantId, femaleId)).thenReturn(female(femaleId));
        when(cycleRepository.existsByTenantIdAndFemaleIdAndStatus(tenantId, femaleId, "PREGNANT"))
                .thenReturn(false);
        ReproductionCycleEntity cycle = cycle(UUID.randomUUID(), "OPEN");
        when(cycleRepository.findFirstByTenantIdAndFemaleIdAndStatusInOrderByCreatedAtDesc(
                any(), any(), any())).thenReturn(Optional.of(cycle));
        when(speciesLookup.requireSpecies(tenantId, speciesId)).thenReturn(species());

        ReproductionDtos.MatingResponse response = service.recordMating(
                new ReproductionDtos.MatingCreateRequest(femaleId, null, null, "NATURAL",
                        Instant.parse("2026-03-01T10:00:00Z"), null));

        assertThat(response.expectedBirthDate()).isEqualTo(LocalDate.of(2026, 12, 9));
        assertThat(response.expectedDiagnosisDate()).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(cycle.status).isEqualTo("MATED");
        verify(matingRepository).save(any(MatingEntity.class));
        verify(auditService).record("MATING_RECORDED", "mating", response.id());
    }

    @Test
    void rejectsMatingForPregnantFemale() {
        when(herdLookup.requireAnimal(tenantId, femaleId)).thenReturn(female(femaleId));
        when(cycleRepository.existsByTenantIdAndFemaleIdAndStatus(tenantId, femaleId, "PREGNANT"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.recordMating(new ReproductionDtos.MatingCreateRequest(
                femaleId, null, null, "NATURAL", Instant.parse("2026-03-01T10:00:00Z"), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("pregnant");
        verify(matingRepository, never()).save(any(MatingEntity.class));
    }

    @Test
    void rejectsMatingWithNonMaleAnimal() {
        UUID maleId = UUID.randomUUID();
        when(herdLookup.requireAnimal(tenantId, femaleId)).thenReturn(female(femaleId));
        when(herdLookup.requireAnimal(tenantId, maleId)).thenReturn(female(maleId));

        assertThatThrownBy(() -> service.recordMating(new ReproductionDtos.MatingCreateRequest(
                femaleId, maleId, null, "NATURAL", Instant.parse("2026-03-01T10:00:00Z"), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not male");
    }

    @Test
    void recordsPositiveDiagnosisAndMarksCyclePregnant() {
        ReproductionCycleEntity cycle = cycle(UUID.randomUUID(), "MATED");
        when(cycleRepository.findByIdAndTenantId(cycle.id, tenantId)).thenReturn(Optional.of(cycle));
        when(matingRepository.existsByTenantIdAndCycleId(tenantId, cycle.id)).thenReturn(true);
        when(herdLookup.requireAnimal(tenantId, femaleId)).thenReturn(female(femaleId));
        when(speciesLookup.requireSpecies(tenantId, speciesId)).thenReturn(species());

        ReproductionDtos.DiagnosisResponse response = service.recordDiagnosis(
                new ReproductionDtos.DiagnosisCreateRequest(cycle.id,
                        Instant.parse("2026-04-05T10:00:00Z"), "POSITIVE", null));

        assertThat(response.result()).isEqualTo("POSITIVE");
        assertThat(response.nextDiagnosisAt()).isNull();
        assertThat(cycle.status).isEqualTo("PREGNANT");
        verify(diagnosisRepository).save(any(PregnancyDiagnosisEntity.class));
        verify(auditService).record("PREGNANCY_DIAGNOSED", "pregnancy_diagnosis", response.id());
    }

    @Test
    void rejectsDiagnosisWithoutMating() {
        ReproductionCycleEntity cycle = cycle(UUID.randomUUID(), "OPEN");
        when(cycleRepository.findByIdAndTenantId(cycle.id, tenantId)).thenReturn(Optional.of(cycle));
        when(matingRepository.existsByTenantIdAndCycleId(tenantId, cycle.id)).thenReturn(false);

        assertThatThrownBy(() -> service.recordDiagnosis(new ReproductionDtos.DiagnosisCreateRequest(
                cycle.id, Instant.parse("2026-04-05T10:00:00Z"), "POSITIVE", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no mating");
    }

    @Test
    void recordsNegativeDiagnosisAndReopensCycle() {
        ReproductionCycleEntity cycle = cycle(UUID.randomUUID(), "MATED");
        when(cycleRepository.findByIdAndTenantId(cycle.id, tenantId)).thenReturn(Optional.of(cycle));
        when(matingRepository.existsByTenantIdAndCycleId(tenantId, cycle.id)).thenReturn(true);
        when(herdLookup.requireAnimal(tenantId, femaleId)).thenReturn(female(femaleId));
        when(speciesLookup.requireSpecies(tenantId, speciesId)).thenReturn(species());

        ReproductionDtos.DiagnosisResponse response = service.recordDiagnosis(
                new ReproductionDtos.DiagnosisCreateRequest(cycle.id,
                        Instant.parse("2026-04-05T10:00:00Z"), "NEGATIVE", null));

        assertThat(response.nextDiagnosisAt()).isEqualTo(LocalDate.of(2026, 4, 26));
        assertThat(cycle.status).isEqualTo("OPEN");
    }

    @Test
    void recordsAbortionAndClosesCycle() {
        ReproductionCycleEntity cycle = cycle(UUID.randomUUID(), "PREGNANT");
        when(cycleRepository.findByIdAndTenantId(cycle.id, tenantId)).thenReturn(Optional.of(cycle));

        ReproductionDtos.AbortionResponse response = service.recordAbortion(
                new ReproductionDtos.AbortionCreateRequest(cycle.id,
                        Instant.parse("2026-06-01T10:00:00Z"), "MID", "Infection"));

        assertThat(response.stage()).isEqualTo("MID");
        assertThat(cycle.status).isEqualTo("ABORTED");
        assertThat(cycle.endDate).isEqualTo(LocalDate.of(2026, 6, 1));
        verify(abortionRepository).save(any(AbortionEntity.class));
        verify(auditService).record("ABORTION_RECORDED", "abortion", response.id());
    }

    @Test
    void rejectsAbortionOnNonPregnantCycle() {
        ReproductionCycleEntity cycle = cycle(UUID.randomUUID(), "MATED");
        when(cycleRepository.findByIdAndTenantId(cycle.id, tenantId)).thenReturn(Optional.of(cycle));

        assertThatThrownBy(() -> service.recordAbortion(new ReproductionDtos.AbortionCreateRequest(
                cycle.id, Instant.parse("2026-06-01T10:00:00Z"), null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not pregnant");
    }

    @Test
    void recordsBirthCreatesLitterAndIncrementsParity() {
        ReproductionCycleEntity cycle = cycle(UUID.randomUUID(), "PREGNANT");
        when(cycleRepository.findByIdAndTenantId(cycle.id, tenantId)).thenReturn(Optional.of(cycle));
        when(herdLookup.requireAnimal(tenantId, femaleId)).thenReturn(female(femaleId));
        when(speciesLookup.requireSpecies(tenantId, speciesId)).thenReturn(species());
        when(matingRepository.findByTenantIdAndCycleIdOrderByMatedAtDesc(tenantId, cycle.id))
                .thenReturn(List.of(new MatingEntity()));

        ReproductionDtos.BirthCreationResponse response = service.recordBirth(
                birthRequest(cycle.id, Instant.parse("2026-12-09T10:00:00Z")));

        assertThat(response.birth().bornAlive()).isEqualTo(6);
        assertThat(response.litterId()).isNotNull();
        assertThat(response.retroactiveMatingSuggested()).isFalse();
        assertThat(cycle.status).isEqualTo("BIRTHED");
        assertThat(cycle.parity).isEqualTo(1);
        verify(birthRepository).save(any(BirthEntity.class));
        verify(litterRepository).save(any(LitterEntity.class));
        verify(auditService).record("FARROWING_RECORDED", "birth", response.birth().id());
    }

    @Test
    void suggestsRetroactiveMatingWhenBirthHasNoMating() {
        ReproductionCycleEntity cycle = cycle(UUID.randomUUID(), "OPEN");
        when(cycleRepository.findByIdAndTenantId(cycle.id, tenantId)).thenReturn(Optional.of(cycle));
        when(herdLookup.requireAnimal(tenantId, femaleId)).thenReturn(female(femaleId));
        when(speciesLookup.requireSpecies(tenantId, speciesId)).thenReturn(species());
        when(matingRepository.findByTenantIdAndCycleIdOrderByMatedAtDesc(tenantId, cycle.id))
                .thenReturn(List.of());

        ReproductionDtos.BirthCreationResponse response = service.recordBirth(
                birthRequest(cycle.id, Instant.parse("2026-12-09T10:00:00Z")));

        assertThat(response.retroactiveMatingSuggested()).isTrue();
        assertThat(response.suggestedMatingDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void rejectsBirthForAnotherFemale() {
        ReproductionCycleEntity cycle = cycle(UUID.randomUUID(), "PREGNANT");
        UUID otherFemaleId = UUID.randomUUID();
        when(cycleRepository.findByIdAndTenantId(cycle.id, tenantId)).thenReturn(Optional.of(cycle));
        when(herdLookup.requireAnimal(tenantId, otherFemaleId)).thenReturn(female(otherFemaleId));

        assertThatThrownBy(() -> service.recordBirth(
                new ReproductionDtos.BirthCreateRequest(cycle.id, otherFemaleId,
                        Instant.parse("2026-12-09T10:00:00Z"), 6, 1, 0,
                        new BigDecimal("12.5"), true, "RAS")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void recordsWeaningAndUpdatesLitter() {
        LitterEntity litter = litter(UUID.randomUUID());
        when(litterRepository.findByIdAndTenantId(litter.id, tenantId))
                .thenReturn(Optional.of(litter));

        ReproductionDtos.WeaningResponse response = service.recordWeaning(
                new ReproductionDtos.WeaningCreateRequest(litter.id,
                        Instant.parse("2027-02-07T10:00:00Z"), 5, new BigDecimal("18.5")));

        assertThat(response.count()).isEqualTo(5);
        assertThat(litter.weanedCount).isEqualTo(5);
        assertThat(litter.weaningDate).isEqualTo(LocalDate.of(2027, 2, 7));
        verify(weaningRepository).save(any(WeaningEntity.class));
        verify(auditService).record("WEANING_RECORDED", "weaning", response.id());
    }

    @Test
    void rejectsWeaningAboveBornAlive() {
        LitterEntity litter = litter(UUID.randomUUID());
        when(litterRepository.findByIdAndTenantId(litter.id, tenantId))
                .thenReturn(Optional.of(litter));

        assertThatThrownBy(() -> service.recordWeaning(new ReproductionDtos.WeaningCreateRequest(
                litter.id, Instant.parse("2027-02-07T10:00:00Z"), 9, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void createsLotFromLitter() {
        LitterEntity litter = litter(UUID.randomUUID());
        litter.weanedCount = 5;
        BirthEntity birth = new BirthEntity();
        birth.id = litter.birthId;
        birth.cycleId = UUID.randomUUID();
        ReproductionCycleEntity cycle = cycle(birth.cycleId, "BIRTHED");
        when(litterRepository.findByIdAndTenantId(litter.id, tenantId))
                .thenReturn(Optional.of(litter));
        when(birthRepository.findByIdAndTenantId(litter.birthId, tenantId))
                .thenReturn(Optional.of(birth));
        when(cycleRepository.findByIdAndTenantId(birth.cycleId, tenantId))
                .thenReturn(Optional.of(cycle));
        when(herdLookup.requireAnimal(tenantId, femaleId)).thenReturn(female(femaleId));
        UUID lotId = UUID.randomUUID();
        when(lotService.create(any(LotDtos.LotCreateRequest.class)))
                .thenReturn(new LotDtos.LotResponse(lotId, farmId, speciesId, "Bande portée",
                        null, null, "OPEN", LocalDate.now(), 5, 5, "CREATED", null, null, 0,
                        Instant.now(), Instant.now()));

        LotDtos.LotResponse response = service.createLotFromLitter(litter.id,
                new ReproductionDtos.LitterLotCreateRequest("Bande portée", "BP", "MEAT", null));

        assertThat(response.id()).isEqualTo(lotId);
        assertThat(litter.lotId).isEqualTo(lotId);
        verify(lotService).create(any(LotDtos.LotCreateRequest.class));
        verify(auditService).record("LITTER_LOT_CREATED", "litter", litter.id);
    }

    @Test
    void rejectsLotCreationWhenLitterAlreadyLinked() {
        LitterEntity litter = litter(UUID.randomUUID());
        litter.lotId = UUID.randomUUID();
        when(litterRepository.findByIdAndTenantId(litter.id, tenantId))
                .thenReturn(Optional.of(litter));

        assertThatThrownBy(() -> service.createLotFromLitter(litter.id,
                new ReproductionDtos.LitterLotCreateRequest("Bande portée", null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already linked");
    }

    @Test
    void listsCyclesWithCursorPagination() {
        ReproductionCycleEntity first = cycle(UUID.randomUUID(), "OPEN");
        ReproductionCycleEntity second = cycle(UUID.randomUUID(), "MATED");
        ReproductionCycleEntity third = cycle(UUID.randomUUID(), "PREGNANT");
        when(cycleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second, third)));

        PageResponse<ReproductionDtos.CycleResponse> response = service.listCycles(femaleId, null, 2, null);

        assertThat(response.items()).hasSize(2);
        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextCursor()).isNotBlank();
    }

    @Test
    void returnsCycleDetails() {
        ReproductionCycleEntity cycle = cycle(UUID.randomUUID(), "MATED");
        when(cycleRepository.findByIdAndTenantId(cycle.id, tenantId)).thenReturn(Optional.of(cycle));
        when(heatRepository.findByTenantIdAndCycleIdOrderByObservedAtDesc(tenantId, cycle.id))
                .thenReturn(List.of());
        when(matingRepository.findByTenantIdAndCycleIdOrderByMatedAtDesc(tenantId, cycle.id))
                .thenReturn(List.of());
        when(diagnosisRepository.findByTenantIdAndCycleIdOrderByDiagnosedAtDesc(tenantId, cycle.id))
                .thenReturn(List.of());
        when(abortionRepository.findByTenantIdAndCycleIdOrderByOccurredAtDesc(tenantId, cycle.id))
                .thenReturn(List.of());
        when(birthRepository.findByTenantIdAndCycleIdOrderByOccurredAtDesc(tenantId, cycle.id))
                .thenReturn(List.of());

        ReproductionDtos.CycleDetailResponse response = service.getCycle(cycle.id);

        assertThat(response.cycle().id()).isEqualTo(cycle.id);
        assertThat(response.heats()).isEmpty();
        assertThat(response.litters()).isEmpty();
    }

    @Test
    void unknownCycleIsNotFound() {
        UUID cycleId = UUID.randomUUID();
        when(cycleRepository.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCycle(cycleId)).isInstanceOf(NotFoundException.class);
    }

    private HerdLookup.AnimalInfo female(UUID id) {
        return new HerdLookup.AnimalInfo(id, farmId, speciesId, "FEMALE", "ACTIVE");
    }

    private HerdLookup.AnimalInfo male(UUID id) {
        return new HerdLookup.AnimalInfo(id, farmId, speciesId, "MALE", "ACTIVE");
    }

    private SpeciesLookup.SpeciesInfo species() {
        return new SpeciesLookup.SpeciesInfo(speciesId, 283, 21, 60,
                Map.of("diagnosisDays", 35));
    }

    private ReproductionCycleEntity cycle(UUID id, String status) {
        ReproductionCycleEntity cycle = new ReproductionCycleEntity();
        cycle.id = id;
        cycle.tenantId = tenantId;
        cycle.farmId = farmId;
        cycle.femaleId = femaleId;
        cycle.status = status;
        cycle.startDate = LocalDate.of(2026, 3, 1);
        cycle.createdAt = Instant.parse("2026-03-01T10:00:00Z");
        cycle.updatedAt = cycle.createdAt;
        return cycle;
    }

    private LitterEntity litter(UUID id) {
        LitterEntity litter = new LitterEntity();
        litter.id = id;
        litter.tenantId = tenantId;
        litter.birthId = UUID.randomUUID();
        litter.name = "Portée 2026-12-09";
        litter.bornAlive = 6;
        litter.weaningDate = LocalDate.of(2027, 2, 7);
        return litter;
    }

    private ReproductionDtos.BirthCreateRequest birthRequest(UUID cycleId, Instant occurredAt) {
        return new ReproductionDtos.BirthCreateRequest(cycleId, femaleId, occurredAt, 6, 1, 0,
                new BigDecimal("12.5"), true, "RAS");
    }
}
