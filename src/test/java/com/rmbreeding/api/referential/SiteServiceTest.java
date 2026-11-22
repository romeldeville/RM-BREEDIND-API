package com.rmbreeding.api.referential;

import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.identity.FarmRepository;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteServiceTest {

    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private PenRepository penRepository;
    @Mock
    private PenMovementRepository penMovementRepository;
    @Mock
    private FarmRepository farmRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AuditService auditService;

    private SiteService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID farmId = UUID.randomUUID();
    private final UUID buildingId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SiteService(buildingRepository, penRepository, penMovementRepository,
                farmRepository, jdbcTemplate, auditService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createsPenUsingBuildingFarm() {
        BuildingEntity building = new BuildingEntity();
        building.id = buildingId;
        building.tenantId = tenantId;
        building.farmId = farmId;
        when(buildingRepository.findByIdAndTenantId(buildingId, tenantId)).thenReturn(Optional.of(building));

        ReferentialDtos.PenResponse response = service.createPen(new ReferentialDtos.PenRequest(
                buildingId, "Loge A", "A", "GROUP", 12));

        assertThat(response.farmId()).isEqualTo(farmId);
        assertThat(response.capacity()).isEqualTo(12);
        verify(penRepository).save(any(PenEntity.class));
    }

    @Test
    void rejectsUnknownBuilding() {
        when(buildingRepository.findByIdAndTenantId(buildingId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPen(new ReferentialDtos.PenRequest(
                buildingId, "Loge A", null, null, 10)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void requiresExactlyOneSubjectForMovement() {
        ReferentialDtos.PenMovementRequest request = new ReferentialDtos.PenMovementRequest(
                UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), null, null);

        assertThatThrownBy(() -> service.move(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Exactly one");
    }

    @Test
    void rejectsUnknownAnimal() {
        UUID penId = UUID.randomUUID();
        PenEntity pen = new PenEntity();
        pen.id = penId;
        pen.tenantId = tenantId;
        pen.farmId = farmId;
        pen.capacity = 10;
        when(penRepository.findByIdAndTenantId(penId, tenantId)).thenReturn(Optional.of(pen));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(0);

        ReferentialDtos.PenMovementRequest request = new ReferentialDtos.PenMovementRequest(
                UUID.randomUUID(), null, null, penId, null, null);

        assertThatThrownBy(() -> service.move(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Animal");
    }

    @Test
    void createsBuildingForFarm() {
        when(farmRepository.existsByIdAndTenantId(farmId, tenantId)).thenReturn(true);

        ReferentialDtos.BuildingResponse response = service.createBuilding(
                new ReferentialDtos.BuildingRequest(farmId, "Étable", "ET", "LIVESTOCK"));

        assertThat(response.farmId()).isEqualTo(farmId);
        assertThat(response.name()).isEqualTo("Étable");
        verify(buildingRepository).save(any(BuildingEntity.class));
        verify(auditService).record("BUILDING_CREATED", "building", response.id());
    }

    @Test
    void rejectsCapacityBelowOccupancy() {
        UUID penId = UUID.randomUUID();
        PenEntity pen = pen(penId, 10);
        when(penRepository.findByIdAndTenantId(penId, tenantId)).thenReturn(Optional.of(pen));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(6);

        assertThatThrownBy(() -> service.patchPen(penId,
                new ReferentialDtos.PenPatchRequest(null, null, null, 5)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Capacity");
    }

    @Test
    void returnsOccupancy() {
        UUID penId = UUID.randomUUID();
        PenEntity pen = pen(penId, 10);
        when(penRepository.findByIdAndTenantId(penId, tenantId)).thenReturn(Optional.of(pen));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(4);

        ReferentialDtos.PenOccupancyResponse response = service.occupancy(penId);

        assertThat(response.capacity()).isEqualTo(10);
        assertThat(response.occupied()).isEqualTo(4);
    }

    @Test
    void rejectsMovementAboveCapacity() {
        UUID penId = UUID.randomUUID();
        PenEntity pen = new PenEntity();
        pen.id = penId;
        pen.tenantId = tenantId;
        pen.farmId = farmId;
        pen.capacity = 2;
        when(penRepository.findByIdAndTenantId(penId, tenantId)).thenReturn(Optional.of(pen));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(1, 2);

        ReferentialDtos.PenMovementRequest request = new ReferentialDtos.PenMovementRequest(
                UUID.randomUUID(), null, null, penId, null, null);

        assertThatThrownBy(() -> service.move(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("capacity exceeded");
    }

    @Test
    void updatesAnimalPenWhenMovingAnimal() {
        UUID penId = UUID.randomUUID();
        PenEntity pen = pen(penId, 10);
        when(penRepository.findByIdAndTenantId(penId, tenantId)).thenReturn(Optional.of(pen));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(1, 0);

        service.move(new ReferentialDtos.PenMovementRequest(
                UUID.randomUUID(), null, null, penId, null, null));

        verify(jdbcTemplate).update(anyString(), any(), any(), any());
    }

    @Test
    void doesNotUpdateAnimalPenForLotMovement() {
        UUID penId = UUID.randomUUID();
        PenEntity pen = pen(penId, 10);
        when(penRepository.findByIdAndTenantId(penId, tenantId)).thenReturn(Optional.of(pen));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(1, 0);

        service.move(new ReferentialDtos.PenMovementRequest(
                null, UUID.randomUUID(), null, penId, null, null));

        verify(jdbcTemplate, org.mockito.Mockito.never()).update(anyString(), any(), any(), any());
    }

    private PenEntity pen(UUID penId, int capacity) {
        PenEntity pen = new PenEntity();
        pen.id = penId;
        pen.tenantId = tenantId;
        pen.farmId = farmId;
        pen.capacity = capacity;
        return pen;
    }
}
