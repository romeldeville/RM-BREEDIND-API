package com.rmbreeding.api.herd;

import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.identity.FarmRepository;
import com.rmbreeding.api.identity.QuotaService;
import com.rmbreeding.api.referential.BreedRepository;
import com.rmbreeding.api.referential.PenRepository;
import com.rmbreeding.api.referential.SpeciesRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnimalServiceTest {

    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private AnimalStatusHistoryRepository historyRepository;
    @Mock
    private LotRepository lotRepository;
    @Mock
    private LotService lotService;
    @Mock
    private FarmRepository farmRepository;
    @Mock
    private SpeciesRepository speciesRepository;
    @Mock
    private BreedRepository breedRepository;
    @Mock
    private PenRepository penRepository;
    @Mock
    private QuotaService quotaService;
    @Mock
    private AuditService auditService;

    private AnimalService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID farmId = UUID.randomUUID();
    private final UUID speciesId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AnimalService(animalRepository, historyRepository, lotRepository, lotService,
                farmRepository, speciesRepository, breedRepository, penRepository, quotaService,
                auditService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createsAnimalWithActiveStatus() {
        when(farmRepository.existsByIdAndTenantId(farmId, tenantId)).thenReturn(true);
        when(speciesRepository.existsByIdAndTenantId(speciesId, tenantId)).thenReturn(true);
        when(animalRepository.existsByTenantIdAndFarmIdAndIdentifier(tenantId, farmId, "A-001"))
                .thenReturn(false);
        when(animalRepository.countByTenantId(tenantId)).thenReturn(0L);

        HerdDtos.AnimalResponse response = service.create(createRequest("A-001", null, null));

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.identifier()).isEqualTo("A-001");
        assertThat(response.birthEstimated()).isTrue();
        assertThat(response.version()).isZero();
        verify(animalRepository).save(any(AnimalEntity.class));
        verify(historyRepository).save(any(AnimalStatusHistoryEntity.class));
        verify(quotaService).setUsed(tenantId, "animals", 1);
        verify(auditService).record("ANIMAL_CREATED", "animal", response.id());
    }

    @Test
    void rejectsDuplicateIdentifier() {
        when(farmRepository.existsByIdAndTenantId(farmId, tenantId)).thenReturn(true);
        when(speciesRepository.existsByIdAndTenantId(speciesId, tenantId)).thenReturn(true);
        when(animalRepository.existsByTenantIdAndFarmIdAndIdentifier(tenantId, farmId, "A-001"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest("A-001", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("identifier");
    }

    @Test
    void rejectsUnknownFarm() {
        when(farmRepository.existsByIdAndTenantId(farmId, tenantId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(createRequest("A-001", null, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Farm");
    }

    @Test
    void rejectsMotherThatIsNotFemale() {
        UUID motherId = UUID.randomUUID();
        when(farmRepository.existsByIdAndTenantId(farmId, tenantId)).thenReturn(true);
        when(speciesRepository.existsByIdAndTenantId(speciesId, tenantId)).thenReturn(true);
        when(animalRepository.existsByTenantIdAndFarmIdAndIdentifier(tenantId, farmId, "A-001"))
                .thenReturn(false);
        when(animalRepository.findByIdAndTenantId(motherId, tenantId))
                .thenReturn(Optional.of(animal(motherId, "ACTIVE", "MALE")));

        assertThatThrownBy(() -> service.create(createRequest("A-001", motherId, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("female");
    }

    @Test
    void rejectsParentFromAnotherFarm() {
        UUID fatherId = UUID.randomUUID();
        when(farmRepository.existsByIdAndTenantId(farmId, tenantId)).thenReturn(true);
        when(speciesRepository.existsByIdAndTenantId(speciesId, tenantId)).thenReturn(true);
        when(animalRepository.existsByTenantIdAndFarmIdAndIdentifier(tenantId, farmId, "A-001"))
                .thenReturn(false);
        AnimalEntity father = animal(fatherId, "ACTIVE", "MALE");
        father.farmId = UUID.randomUUID();
        when(animalRepository.findByIdAndTenantId(fatherId, tenantId)).thenReturn(Optional.of(father));

        assertThatThrownBy(() -> service.create(createRequest("A-001", null, fatherId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("same farm");
    }

    @Test
    void patchesAnimalFields() {
        UUID animalId = UUID.randomUUID();
        AnimalEntity entity = animal(animalId, "ACTIVE", "FEMALE");
        when(animalRepository.findByIdAndTenantId(animalId, tenantId)).thenReturn(Optional.of(entity));

        HerdDtos.AnimalResponse response = service.patch(animalId, new HerdDtos.AnimalPatchRequest(
                null, "Bella", null, null, null, null, null, null, null, null,
                new BigDecimal("48.5"), null));

        assertThat(response.name()).isEqualTo("Bella");
        assertThat(response.weightKg()).isEqualByComparingTo("48.5");
        verify(animalRepository).save(entity);
        verify(auditService).record("ANIMAL_UPDATED", "animal", animalId);
    }

    @Test
    void rejectsPatchOnFinalAnimal() {
        UUID animalId = UUID.randomUUID();
        AnimalEntity entity = animal(animalId, "SOLD", "FEMALE");
        when(animalRepository.findByIdAndTenantId(animalId, tenantId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.patch(animalId, new HerdDtos.AnimalPatchRequest(
                null, "Bella", null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("final state");
    }

    @Test
    void rejectsAnimalAsItsOwnParent() {
        UUID animalId = UUID.randomUUID();
        AnimalEntity entity = animal(animalId, "ACTIVE", "FEMALE");
        when(animalRepository.findByIdAndTenantId(animalId, tenantId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.patch(animalId, new HerdDtos.AnimalPatchRequest(
                null, null, null, null, null, null, null, null, animalId, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("own parent");
    }

    @Test
    void changesStatusAndRecordsHistory() {
        UUID animalId = UUID.randomUUID();
        AnimalEntity entity = animal(animalId, "ACTIVE", "FEMALE");
        when(animalRepository.findByIdAndTenantId(animalId, tenantId)).thenReturn(Optional.of(entity));

        HerdDtos.AnimalStatusResponse response = service.changeStatus(animalId,
                new HerdDtos.AnimalStatusRequest("SOLD", "Vente directe", null));

        assertThat(response.status()).isEqualTo("SOLD");
        assertThat(entity.status).isEqualTo("SOLD");
        verify(animalRepository).save(entity);
        verify(historyRepository).save(any(AnimalStatusHistoryEntity.class));
        verify(auditService).record("ANIMAL_STATUS_CHANGED", "animal", animalId);
    }

    @Test
    void rejectsStatusChangeFromFinalState() {
        UUID animalId = UUID.randomUUID();
        AnimalEntity entity = animal(animalId, "DEAD", "FEMALE");
        when(animalRepository.findByIdAndTenantId(animalId, tenantId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.changeStatus(animalId,
                new HerdDtos.AnimalStatusRequest("ACTIVE", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("final state");
    }

    @Test
    void returnsStatusHistory() {
        UUID animalId = UUID.randomUUID();
        AnimalEntity entity = animal(animalId, "ACTIVE", "FEMALE");
        when(animalRepository.findByIdAndTenantId(animalId, tenantId)).thenReturn(Optional.of(entity));
        AnimalStatusHistoryEntity history = new AnimalStatusHistoryEntity();
        history.id = UUID.randomUUID();
        history.tenantId = tenantId;
        history.animalId = animalId;
        history.status = "ACTIVE";
        history.changedAt = Instant.now();
        when(historyRepository.findByTenantIdAndAnimalIdOrderByChangedAtDesc(tenantId, animalId))
                .thenReturn(List.of(history));

        List<HerdDtos.AnimalStatusResponse> response = service.statusHistory(animalId);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().status()).isEqualTo("ACTIVE");
    }

    @Test
    void listsWithFiltersAndCursorPagination() {
        AnimalEntity first = animal(UUID.randomUUID(), "ACTIVE", "FEMALE");
        AnimalEntity second = animal(UUID.randomUUID(), "ACTIVE", "MALE");
        AnimalEntity third = animal(UUID.randomUUID(), "ACTIVE", "FEMALE");
        when(animalRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second, third)));

        PageResponse<HerdDtos.AnimalResponse> response = service.list("A-00", "ACTIVE", speciesId, null,
                null, 2, null);

        assertThat(response.items()).hasSize(2);
        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextCursor()).isNotBlank();
    }

    @Test
    void returnsEmptyPageWhenLotHasNoAnimal() {
        UUID lotId = UUID.randomUUID();
        when(animalRepository.findAnimalIdsInLot(lotId)).thenReturn(List.of());

        PageResponse<HerdDtos.AnimalResponse> response = service.list(null, null, null, null, lotId, 50, null);

        assertThat(response.items()).isEmpty();
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void rejectsInvalidCursor() {
        assertThatThrownBy(() -> service.list(null, null, null, null, null, 50, "not-a-cursor"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cursor");
    }

    @Test
    void unknownAnimalIsNotFound() {
        UUID animalId = UUID.randomUUID();
        when(animalRepository.findByIdAndTenantId(animalId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(animalId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void individualizesAnimalFromLot() {
        UUID lotId = UUID.randomUUID();
        LotEntity lot = lot(lotId, "OPEN", 3);
        when(lotRepository.findByIdAndTenantId(lotId, tenantId)).thenReturn(Optional.of(lot));
        when(animalRepository.existsByTenantIdAndFarmIdAndIdentifier(tenantId, farmId, "A-100"))
                .thenReturn(false);
        when(animalRepository.countByTenantId(tenantId)).thenReturn(0L);

        HerdDtos.AnimalResponse response = service.individualize(lotId,
                new LotDtos.IndividualizeRequest("A-100", "FEMALE", LocalDate.of(2026, 1, 1)));

        assertThat(response.identifier()).isEqualTo("A-100");
        assertThat(response.origin()).isEqualTo("LOT");
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(animalRepository).save(any(AnimalEntity.class));
        verify(quotaService).setUsed(tenantId, "animals", 1);
        verify(lotService).move(any(UUID.class), any(LotDtos.LotMovementRequest.class));
        verify(auditService).record("ANIMAL_INDIVIDUALIZED", "animal", response.id());
    }

    @Test
    void rejectsIndividualizationFromEmptyLot() {
        UUID lotId = UUID.randomUUID();
        when(lotRepository.findByIdAndTenantId(lotId, tenantId))
                .thenReturn(Optional.of(lot(lotId, "OPEN", 0)));

        assertThatThrownBy(() -> service.individualize(lotId,
                new LotDtos.IndividualizeRequest("A-100", "FEMALE", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsIndividualizationFromClosedLot() {
        UUID lotId = UUID.randomUUID();
        when(lotRepository.findByIdAndTenantId(lotId, tenantId))
                .thenReturn(Optional.of(lot(lotId, "CLOSED", 2)));

        assertThatThrownBy(() -> service.individualize(lotId,
                new LotDtos.IndividualizeRequest("A-100", "FEMALE", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("closed");
    }

    private LotEntity lot(UUID lotId, String status, int currentCount) {
        LotEntity lot = new LotEntity();
        lot.id = lotId;
        lot.tenantId = tenantId;
        lot.farmId = farmId;
        lot.speciesId = speciesId;
        lot.name = "Lot test";
        lot.status = status;
        lot.initialCount = currentCount;
        lot.currentCount = currentCount;
        lot.createdAt = Instant.parse("2026-01-01T10:00:00Z");
        lot.updatedAt = lot.createdAt;
        return lot;
    }

    private HerdDtos.AnimalCreateRequest createRequest(String identifier, UUID motherId, UUID fatherId) {
        return new HerdDtos.AnimalCreateRequest(farmId, speciesId, null, null, identifier, "Bella", "FEMALE",
                LocalDate.of(2025, 1, 15), true, "NAISSANCE", motherId, fatherId,
                new BigDecimal("42.0"), LocalDate.of(2025, 1, 15));
    }

    private AnimalEntity animal(UUID animalId, String status, String sex) {
        AnimalEntity animal = new AnimalEntity();
        animal.id = animalId;
        animal.tenantId = tenantId;
        animal.farmId = farmId;
        animal.speciesId = speciesId;
        animal.identifier = "A-001";
        animal.sex = sex;
        animal.status = status;
        animal.birthEstimated = false;
        animal.createdAt = Instant.parse("2026-01-01T10:00:00Z");
        animal.updatedAt = animal.createdAt;
        return animal;
    }
}
