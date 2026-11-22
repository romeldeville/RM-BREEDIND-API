package com.rmbreeding.api.referential;

import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.identity.AuthContext;
import com.rmbreeding.api.identity.AuthPrincipal;
import com.rmbreeding.api.identity.FarmRepository;
import com.rmbreeding.api.shared.BusinessRuleException;
import com.rmbreeding.api.shared.ErrorCode;
import com.rmbreeding.api.shared.NotFoundException;
import com.rmbreeding.api.shared.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SiteService {

    private final BuildingRepository buildingRepository;
    private final PenRepository penRepository;
    private final PenMovementRepository penMovementRepository;
    private final FarmRepository farmRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    public SiteService(BuildingRepository buildingRepository, PenRepository penRepository,
            PenMovementRepository penMovementRepository, FarmRepository farmRepository,
            JdbcTemplate jdbcTemplate, AuditService auditService) {
        this.buildingRepository = buildingRepository;
        this.penRepository = penRepository;
        this.penMovementRepository = penMovementRepository;
        this.farmRepository = farmRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    public List<ReferentialDtos.BuildingResponse> buildings(UUID farmId) {
        UUID tenantId = TenantContext.requireTenantId();
        requireFarm(farmId, tenantId);
        return buildingRepository.findByTenantIdAndFarmIdAndArchivedAtIsNullOrderByName(tenantId, farmId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReferentialDtos.BuildingResponse createBuilding(ReferentialDtos.BuildingRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        requireFarm(request.farmId(), tenantId);
        Instant now = Instant.now();
        BuildingEntity building = new BuildingEntity();
        building.id = UUID.randomUUID();
        building.tenantId = tenantId;
        building.farmId = request.farmId();
        building.name = request.name();
        building.code = request.code();
        building.type = request.type();
        building.createdAt = now;
        building.updatedAt = now;
        buildingRepository.save(building);
        auditService.record("BUILDING_CREATED", "building", building.id);
        return toResponse(building);
    }

    @Transactional
    public ReferentialDtos.BuildingResponse patchBuilding(UUID buildingId,
            ReferentialDtos.BuildingPatchRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        BuildingEntity building = requireBuilding(buildingId, tenantId);
        if (request.name() != null) {
            building.name = request.name();
        }
        if (request.code() != null) {
            building.code = request.code();
        }
        if (request.type() != null) {
            building.type = request.type();
        }
        building.updatedAt = Instant.now();
        buildingRepository.save(building);
        return toResponse(building);
    }

    @Transactional
    public void archiveBuilding(UUID buildingId) {
        UUID tenantId = TenantContext.requireTenantId();
        BuildingEntity building = requireBuilding(buildingId, tenantId);
        building.archivedAt = Instant.now();
        building.updatedAt = building.archivedAt;
        buildingRepository.save(building);
    }

    public List<ReferentialDtos.PenResponse> pens(UUID buildingId) {
        UUID tenantId = TenantContext.requireTenantId();
        requireBuilding(buildingId, tenantId);
        return penRepository.findByTenantIdAndBuildingIdAndArchivedAtIsNullOrderByName(tenantId, buildingId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReferentialDtos.PenResponse createPen(ReferentialDtos.PenRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        BuildingEntity building = requireBuilding(request.buildingId(), tenantId);
        Instant now = Instant.now();
        PenEntity pen = new PenEntity();
        pen.id = UUID.randomUUID();
        pen.tenantId = tenantId;
        pen.farmId = building.farmId;
        pen.buildingId = building.id;
        pen.name = request.name();
        pen.code = request.code();
        pen.type = request.type();
        pen.capacity = request.capacity();
        pen.createdAt = now;
        pen.updatedAt = now;
        penRepository.save(pen);
        auditService.record("PEN_CREATED", "pen", pen.id);
        return toResponse(pen);
    }

    @Transactional
    public ReferentialDtos.PenResponse patchPen(UUID penId, ReferentialDtos.PenPatchRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        PenEntity pen = requirePen(penId, tenantId);
        if (request.name() != null) {
            pen.name = request.name();
        }
        if (request.code() != null) {
            pen.code = request.code();
        }
        if (request.type() != null) {
            pen.type = request.type();
        }
        if (request.capacity() != null) {
            int occupied = countAnimalsInPen(tenantId, penId);
            if (request.capacity() < occupied) {
                throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Capacity cannot be lower than current occupancy (" + occupied + ")");
            }
            pen.capacity = request.capacity();
        }
        pen.updatedAt = Instant.now();
        penRepository.save(pen);
        return toResponse(pen);
    }

    @Transactional
    public void archivePen(UUID penId) {
        UUID tenantId = TenantContext.requireTenantId();
        PenEntity pen = requirePen(penId, tenantId);
        pen.archivedAt = Instant.now();
        pen.updatedAt = pen.archivedAt;
        penRepository.save(pen);
    }

    public List<ReferentialDtos.PenMovementResponse> movements(UUID penId) {
        UUID tenantId = TenantContext.requireTenantId();
        List<PenMovementEntity> entities = penId == null
                ? penMovementRepository.findByTenantIdOrderByMovedAtDesc(tenantId)
                : penMovementRepository.findByTenantIdAndToPenIdOrderByMovedAtDesc(tenantId, penId);
        return entities.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReferentialDtos.PenMovementResponse move(ReferentialDtos.PenMovementRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        boolean hasAnimal = request.animalId() != null;
        boolean hasLot = request.lotId() != null;
        if (hasAnimal == hasLot) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Exactly one of animalId or lotId must be provided");
        }
        PenEntity target = requirePen(request.toPenId(), tenantId);
        if (request.fromPenId() != null) {
            requirePen(request.fromPenId(), tenantId);
        }
        if (hasAnimal) {
            requireAnimal(tenantId, request.animalId());
        } else {
            requireLot(tenantId, request.lotId());
        }
        int occupied = countAnimalsInPen(tenantId, target.id);
        int incoming = hasAnimal ? 1 : 0;
        if (target.capacity > 0 && occupied + incoming > target.capacity) {
            throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Pen capacity exceeded (" + occupied + "+" + incoming + "/" + target.capacity + ")");
        }
        PenMovementEntity movement = new PenMovementEntity();
        movement.id = UUID.randomUUID();
        movement.tenantId = tenantId;
        movement.farmId = target.farmId;
        movement.animalId = request.animalId();
        movement.lotId = request.lotId();
        movement.fromPenId = request.fromPenId();
        movement.toPenId = target.id;
        movement.movedAt = request.movedAt() == null ? Instant.now() : request.movedAt();
        movement.reason = request.reason();
        movement.actorId = currentUserId();
        movement.createdAt = Instant.now();
        penMovementRepository.save(movement);
        if (hasAnimal) {
            jdbcTemplate.update("""
                    WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                    UPDATE animals SET pen_id = ?, updated_at = now()
                    FROM ctx WHERE animals.id = ?
                    """, tenantId.toString(), target.id, request.animalId());
        }
        auditService.record("PEN_MOVEMENT", "pen", target.id);
        return toResponse(movement);
    }

    public ReferentialDtos.PenOccupancyResponse occupancy(UUID penId) {
        UUID tenantId = TenantContext.requireTenantId();
        PenEntity pen = requirePen(penId, tenantId);
        return new ReferentialDtos.PenOccupancyResponse(pen.id, pen.capacity, countAnimalsInPen(tenantId, penId));
    }

    public int countAnimalsInPen(UUID tenantId, UUID penId) {
        Integer count = jdbcTemplate.queryForObject("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                SELECT count(*) FROM animals, ctx WHERE pen_id = ?
                """, Integer.class, tenantId.toString(), penId);
        return count == null ? 0 : count;
    }

    private UUID currentUserId() {
        AuthPrincipal principal = AuthContext.get();
        return principal == null ? null : principal.userId();
    }

    private void requireAnimal(UUID tenantId, UUID animalId) {
        Integer count = jdbcTemplate.queryForObject("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                SELECT count(*) FROM animals, ctx WHERE id = ?
                """, Integer.class, tenantId.toString(), animalId);
        if (count == null || count == 0) {
            throw new NotFoundException("Animal not found");
        }
    }

    private void requireLot(UUID tenantId, UUID lotId) {
        Integer count = jdbcTemplate.queryForObject("""
                WITH ctx AS MATERIALIZED (SELECT set_config('app.tenant_id', ?, false))
                SELECT count(*) FROM lots, ctx WHERE id = ?
                """, Integer.class, tenantId.toString(), lotId);
        if (count == null || count == 0) {
            throw new NotFoundException("Lot not found");
        }
    }

    private void requireFarm(UUID farmId, UUID tenantId) {
        if (!farmRepository.existsByIdAndTenantId(farmId, tenantId)) {
            throw new NotFoundException("Farm not found");
        }
    }

    private BuildingEntity requireBuilding(UUID buildingId, UUID tenantId) {
        return buildingRepository.findByIdAndTenantId(buildingId, tenantId)
                .orElseThrow(() -> new NotFoundException("Building not found"));
    }

    private PenEntity requirePen(UUID penId, UUID tenantId) {
        return penRepository.findByIdAndTenantId(penId, tenantId)
                .orElseThrow(() -> new NotFoundException("Pen not found"));
    }

    private ReferentialDtos.BuildingResponse toResponse(BuildingEntity building) {
        return new ReferentialDtos.BuildingResponse(building.id, building.farmId, building.name,
                building.code, building.type, building.archivedAt);
    }

    private ReferentialDtos.PenResponse toResponse(PenEntity pen) {
        return new ReferentialDtos.PenResponse(pen.id, pen.farmId, pen.buildingId, pen.name, pen.code,
                pen.type, pen.capacity, pen.archivedAt);
    }

    private ReferentialDtos.PenMovementResponse toResponse(PenMovementEntity movement) {
        return new ReferentialDtos.PenMovementResponse(movement.id, movement.animalId, movement.lotId,
                movement.fromPenId, movement.toPenId, movement.movedAt, movement.reason);
    }
}
