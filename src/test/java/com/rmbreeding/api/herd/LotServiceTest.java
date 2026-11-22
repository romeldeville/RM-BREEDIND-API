package com.rmbreeding.api.herd;

import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.identity.FarmRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LotServiceTest {

    @Mock
    private LotRepository lotRepository;
    @Mock
    private LotMovementRepository movementRepository;
    @Mock
    private LotClosureRepository closureRepository;
    @Mock
    private FarmRepository farmRepository;
    @Mock
    private SpeciesRepository speciesRepository;
    @Mock
    private AuditService auditService;

    private LotService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID farmId = UUID.randomUUID();
    private final UUID speciesId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LotService(lotRepository, movementRepository, closureRepository, farmRepository,
                speciesRepository, auditService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createsLotAndRecordsInitialEntry() {
        when(farmRepository.existsByIdAndTenantId(farmId, tenantId)).thenReturn(true);
        when(speciesRepository.existsByIdAndTenantId(speciesId, tenantId)).thenReturn(true);

        LotDtos.LotResponse response = service.create(createRequest(5));

        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.initialCount()).isEqualTo(5);
        assertThat(response.currentCount()).isEqualTo(5);
        verify(lotRepository, times(2)).save(any(LotEntity.class));
        verify(movementRepository).save(any(LotMovementEntity.class));
        verify(auditService).record("LOT_CREATED", "lot", response.id());
    }

    @Test
    void rejectsUnknownFarm() {
        when(farmRepository.existsByIdAndTenantId(farmId, tenantId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(createRequest(1)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Farm");
    }

    @Test
    void rejectsUnknownSpecies() {
        when(farmRepository.existsByIdAndTenantId(farmId, tenantId)).thenReturn(true);
        when(speciesRepository.existsByIdAndTenantId(speciesId, tenantId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(createRequest(1)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Species");
    }

    @Test
    void patchesLotFields() {
        UUID lotId = UUID.randomUUID();
        LotEntity lot = lot(lotId, "OPEN", 4, 4);
        when(lotRepository.findByIdAndTenantId(lotId, tenantId)).thenReturn(Optional.of(lot));

        LotDtos.LotResponse response = service.patch(lotId,
                new LotDtos.LotPatchRequest("Bandes 2026", "B26", "MEAT", null,
                        new BigDecimal("12.50")));

        assertThat(response.name()).isEqualTo("Bandes 2026");
        assertThat(response.unitCost()).isEqualByComparingTo("12.50");
        verify(auditService).record("LOT_UPDATED", "lot", lotId);
    }

    @Test
    void rejectsPatchOnClosedLot() {
        UUID lotId = UUID.randomUUID();
        when(lotRepository.findByIdAndTenantId(lotId, tenantId))
                .thenReturn(Optional.of(lot(lotId, "CLOSED", 4, 0)));

        assertThatThrownBy(() -> service.patch(lotId,
                new LotDtos.LotPatchRequest("Bandes", null, null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void recordsEntryMovement() {
        UUID lotId = UUID.randomUUID();
        LotEntity lot = lot(lotId, "OPEN", 5, 5);
        when(lotRepository.findByIdAndTenantId(lotId, tenantId)).thenReturn(Optional.of(lot));

        LotDtos.LotMovementResponse response = service.move(lotId,
                new LotDtos.LotMovementRequest("ENTRY", 3, Instant.now(), "Achat"));

        assertThat(response.quantity()).isEqualTo(3);
        assertThat(lot.currentCount).isEqualTo(8);
        verify(auditService).record("LOT_MOVEMENT", "lot", lotId);
    }

    @Test
    void rejectsExitAboveHeadcount() {
        UUID lotId = UUID.randomUUID();
        when(lotRepository.findByIdAndTenantId(lotId, tenantId))
                .thenReturn(Optional.of(lot(lotId, "OPEN", 10, 2)));

        assertThatThrownBy(() -> service.move(lotId,
                new LotDtos.LotMovementRequest("EXIT", 3, Instant.now(), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Insufficient");
    }

    @Test
    void rejectsMovementOnClosedLot() {
        UUID lotId = UUID.randomUUID();
        when(lotRepository.findByIdAndTenantId(lotId, tenantId))
                .thenReturn(Optional.of(lot(lotId, "CLOSED", 10, 0)));

        assertThatThrownBy(() -> service.move(lotId,
                new LotDtos.LotMovementRequest("ENTRY", 1, Instant.now(), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("closed");
        verify(movementRepository, never()).save(any(LotMovementEntity.class));
    }

    @Test
    void closesLotAndComputesMortalityRate() {
        UUID lotId = UUID.randomUUID();
        LotEntity lot = lot(lotId, "OPEN", 10, 0);
        lot.unitCost = new BigDecimal("2.50");
        when(lotRepository.findByIdAndTenantId(lotId, tenantId)).thenReturn(Optional.of(lot));
        when(movementRepository.findByTenantIdAndLotIdOrderByOccurredAtDesc(tenantId, lotId))
                .thenReturn(List.of(movement(lotId, "MORTALITY", 2), movement(lotId, "EXIT", 8)));

        LotDtos.LotClosureResponse response = service.close(lotId);

        assertThat(response.mortalityRate()).isEqualByComparingTo("0.2000");
        assertThat(response.cost()).isEqualByComparingTo("25.00");
        assertThat(lot.status).isEqualTo("CLOSED");
        verify(closureRepository).save(any(LotClosureEntity.class));
        verify(auditService).record("LOT_CLOSED", "lot", lotId);
    }

    @Test
    void rejectsCloseWithRemainingAnimals() {
        UUID lotId = UUID.randomUUID();
        when(lotRepository.findByIdAndTenantId(lotId, tenantId))
                .thenReturn(Optional.of(lot(lotId, "OPEN", 10, 3)));

        assertThatThrownBy(() -> service.close(lotId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("still has animals");
        verify(closureRepository, never()).save(any(LotClosureEntity.class));
    }

    @Test
    void splitsLotIntoNewOpenLot() {
        UUID lotId = UUID.randomUUID();
        LotEntity source = lot(lotId, "OPEN", 10, 10);
        when(lotRepository.findByIdAndTenantId(lotId, tenantId)).thenReturn(Optional.of(source));

        LotDtos.LotResponse created = service.split(lotId, new LotDtos.LotSplitRequest("Bandes B", 4));

        assertThat(created.name()).isEqualTo("Bandes B");
        assertThat(created.currentCount()).isEqualTo(4);
        assertThat(created.origin()).isEqualTo("SPLIT");
        assertThat(source.currentCount).isEqualTo(6);
        verify(auditService).record("LOT_SPLIT", "lot", created.id());
    }

    @Test
    void rejectsSplitAboveRemainingHeadcount() {
        UUID lotId = UUID.randomUUID();
        when(lotRepository.findByIdAndTenantId(lotId, tenantId))
                .thenReturn(Optional.of(lot(lotId, "OPEN", 10, 4)));

        assertThatThrownBy(() -> service.split(lotId, new LotDtos.LotSplitRequest("Bandes B", 4)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("less than");
    }

    @Test
    void mergesSourceLotIntoTarget() {
        UUID targetId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        LotEntity target = lot(targetId, "OPEN", 5, 5);
        LotEntity source = lot(sourceId, "OPEN", 3, 3);
        when(lotRepository.findByIdAndTenantId(targetId, tenantId)).thenReturn(Optional.of(target));
        when(lotRepository.findByIdAndTenantId(sourceId, tenantId)).thenReturn(Optional.of(source));

        LotDtos.LotResponse response = service.merge(targetId, sourceId);

        assertThat(response.currentCount()).isEqualTo(8);
        assertThat(source.status).isEqualTo("CLOSED");
        assertThat(source.currentCount).isZero();
        verify(auditService).record("LOT_MERGED", "lot", targetId);
    }

    @Test
    void rejectsMergeAcrossSpecies() {
        UUID targetId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        LotEntity target = lot(targetId, "OPEN", 5, 5);
        LotEntity source = lot(sourceId, "OPEN", 3, 3);
        source.speciesId = UUID.randomUUID();
        when(lotRepository.findByIdAndTenantId(targetId, tenantId)).thenReturn(Optional.of(target));
        when(lotRepository.findByIdAndTenantId(sourceId, tenantId)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.merge(targetId, sourceId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("same farm and species");
    }

    @Test
    void listsLotsWithCursorPagination() {
        LotEntity first = lot(UUID.randomUUID(), "OPEN", 5, 5);
        LotEntity second = lot(UUID.randomUUID(), "OPEN", 2, 2);
        LotEntity third = lot(UUID.randomUUID(), "OPEN", 1, 1);
        when(lotRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second, third)));

        PageResponse<LotDtos.LotResponse> response = service.list("OPEN", speciesId, 2, null);

        assertThat(response.items()).hasSize(2);
        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextCursor()).isNotBlank();
    }

    @Test
    void returnsLotMovements() {
        UUID lotId = UUID.randomUUID();
        when(lotRepository.findByIdAndTenantId(lotId, tenantId))
                .thenReturn(Optional.of(lot(lotId, "OPEN", 5, 5)));
        when(movementRepository.findByTenantIdAndLotIdOrderByOccurredAtDesc(tenantId, lotId))
                .thenReturn(List.of(movement(lotId, "ENTRY", 5)));

        List<LotDtos.LotMovementResponse> response = service.movements(lotId);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().type()).isEqualTo("ENTRY");
    }

    private LotDtos.LotCreateRequest createRequest(int initialCount) {
        return new LotDtos.LotCreateRequest(farmId, speciesId, "Bandes 2026", "B26", "MEAT",
                LocalDate.of(2026, 2, 1), initialCount, new BigDecimal("10.00"));
    }

    private LotEntity lot(UUID lotId, String status, int initialCount, int currentCount) {
        LotEntity lot = new LotEntity();
        lot.id = lotId;
        lot.tenantId = tenantId;
        lot.farmId = farmId;
        lot.speciesId = speciesId;
        lot.name = "Bandes 2026";
        lot.status = status;
        lot.entryDate = LocalDate.of(2026, 2, 1);
        lot.initialCount = initialCount;
        lot.currentCount = currentCount;
        lot.origin = "CREATED";
        lot.createdAt = Instant.parse("2026-01-01T10:00:00Z");
        lot.updatedAt = lot.createdAt;
        return lot;
    }

    private LotMovementEntity movement(UUID lotId, String type, int quantity) {
        LotMovementEntity movement = new LotMovementEntity();
        movement.id = UUID.randomUUID();
        movement.tenantId = tenantId;
        movement.lotId = lotId;
        movement.type = type;
        movement.quantity = quantity;
        movement.occurredAt = Instant.now();
        movement.createdAt = movement.occurredAt;
        return movement;
    }
}
