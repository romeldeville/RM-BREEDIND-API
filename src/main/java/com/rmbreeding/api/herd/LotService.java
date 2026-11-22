package com.rmbreeding.api.herd;

import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.identity.AuthContext;
import com.rmbreeding.api.identity.FarmRepository;
import com.rmbreeding.api.referential.SpeciesRepository;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class LotService {

    private static final Set<String> INCREASE_TYPES = Set.of("ENTRY", "ADJUSTMENT");

    private final LotRepository lotRepository;
    private final LotMovementRepository movementRepository;
    private final LotClosureRepository closureRepository;
    private final FarmRepository farmRepository;
    private final SpeciesRepository speciesRepository;
    private final AuditService auditService;

    public LotService(LotRepository lotRepository,
            LotMovementRepository movementRepository,
            LotClosureRepository closureRepository,
            FarmRepository farmRepository,
            SpeciesRepository speciesRepository,
            AuditService auditService) {
        this.lotRepository = lotRepository;
        this.movementRepository = movementRepository;
        this.closureRepository = closureRepository;
        this.farmRepository = farmRepository;
        this.speciesRepository = speciesRepository;
        this.auditService = auditService;
    }

    public long openCount() {
        UUID tenantId = TenantContext.requireTenantId();
        return lotRepository.countByTenantIdAndStatus(tenantId, "OPEN");
    }

    public PageResponse<LotDtos.LotResponse> list(String status, UUID speciesId, Integer limit, String cursor) {
        UUID tenantId = TenantContext.requireTenantId();
        int size = limit == null ? 50 : Math.max(1, Math.min(limit, 100));
        Cursor decoded = Cursor.decode(cursor);

        Specification<LotEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("tenantId"), tenantId));
            if (status != null && !status.isBlank()) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (speciesId != null) {
                predicates.add(builder.equal(root.get("speciesId"), speciesId));
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

        Page<LotEntity> page = lotRepository.findAll(specification,
                PageRequest.of(0, size + 1, Sort.by(
                        Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        List<LotEntity> entities = new ArrayList<>(page.getContent());
        boolean hasMore = entities.size() > size;
        if (hasMore) {
            entities = new ArrayList<>(entities.subList(0, size));
        }
        String nextCursor = null;
        if (hasMore && !entities.isEmpty()) {
            LotEntity last = entities.getLast();
            nextCursor = new Cursor(last.createdAt, last.id).encode();
        }
        return new PageResponse<>(entities.stream().map(this::toResponse).toList(), nextCursor, hasMore);
    }

    public LotDtos.LotResponse get(UUID lotId) {
        return toResponse(requireLot(lotId, TenantContext.requireTenantId()));
    }

    @Transactional
    public LotDtos.LotResponse create(LotDtos.LotCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        requireFarm(request.farmId(), tenantId);
        requireSpecies(request.speciesId(), tenantId);

        Instant now = Instant.now();
        LotEntity lot = new LotEntity();
        lot.id = UUID.randomUUID();
        lot.tenantId = tenantId;
        lot.farmId = request.farmId();
        lot.speciesId = request.speciesId();
        lot.name = request.name();
        lot.code = request.code();
        lot.productionType = request.productionType();
        lot.status = "OPEN";
        lot.entryDate = request.entryDate() == null ? LocalDate.now() : request.entryDate();
        lot.initialCount = request.initialCount();
        lot.currentCount = 0;
        lot.origin = "CREATED";
        lot.unitCost = request.unitCost();
        lot.createdAt = now;
        lot.updatedAt = now;
        lotRepository.save(lot);

        if (request.initialCount() > 0) {
            applyMovement(lot, "ENTRY", request.initialCount(), now, "Initial entry");
        }
        auditService.record("LOT_CREATED", "lot", lot.id);
        return toResponse(lot);
    }

    @Transactional
    public LotDtos.LotResponse patch(UUID lotId, LotDtos.LotPatchRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        LotEntity lot = requireLot(lotId, tenantId);
        if ("CLOSED".equals(lot.status)) {
            throw new ConflictException("Lot is closed");
        }
        if (request.name() != null) {
            lot.name = request.name();
        }
        if (request.code() != null) {
            lot.code = request.code();
        }
        if (request.productionType() != null) {
            lot.productionType = request.productionType();
        }
        if (request.entryDate() != null) {
            lot.entryDate = request.entryDate();
        }
        if (request.unitCost() != null) {
            lot.unitCost = request.unitCost();
        }
        lot.updatedAt = Instant.now();
        lotRepository.save(lot);
        auditService.record("LOT_UPDATED", "lot", lot.id);
        return toResponse(lot);
    }

    @Transactional
    public LotDtos.LotMovementResponse move(UUID lotId, LotDtos.LotMovementRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        LotEntity lot = requireLot(lotId, tenantId);
        return toMovementResponse(applyMovement(lot, request.type(), request.quantity(),
                request.occurredAt(), request.reason()));
    }

    public List<LotDtos.LotMovementResponse> movements(UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        requireLot(lotId, tenantId);
        return movementRepository.findByTenantIdAndLotIdOrderByOccurredAtDesc(tenantId, lotId)
                .stream().map(this::toMovementResponse).toList();
    }

    @Transactional
    public LotDtos.LotClosureResponse close(UUID lotId) {
        UUID tenantId = TenantContext.requireTenantId();
        LotEntity lot = requireLot(lotId, tenantId);
        if ("CLOSED".equals(lot.status)) {
            throw new ConflictException("Lot is already closed");
        }
        if (lot.currentCount != 0) {
            throw new ConflictException("Lot still has animals");
        }

        List<LotMovementEntity> movements = movementRepository
                .findByTenantIdAndLotIdOrderByOccurredAtDesc(tenantId, lotId);
        int mortality = movements.stream()
                .filter(movement -> "MORTALITY".equals(movement.type))
                .mapToInt(movement -> movement.quantity)
                .sum();
        BigDecimal mortalityRate = lot.initialCount == 0
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(mortality)
                        .divide(BigDecimal.valueOf(lot.initialCount), 4, RoundingMode.HALF_UP);
        BigDecimal cost = lot.unitCost == null ? null
                : lot.unitCost.multiply(BigDecimal.valueOf(lot.initialCount));

        Instant now = Instant.now();
        LotClosureEntity closure = new LotClosureEntity();
        closure.id = UUID.randomUUID();
        closure.tenantId = tenantId;
        closure.lotId = lot.id;
        closure.mortalityRate = mortalityRate;
        closure.cost = cost;
        closure.closedAt = now;
        closureRepository.save(closure);

        lot.status = "CLOSED";
        lot.closedAt = now;
        lot.updatedAt = now;
        lotRepository.save(lot);
        auditService.record("LOT_CLOSED", "lot", lot.id);
        return toClosureResponse(closure);
    }

    @Transactional
    public LotDtos.LotResponse split(UUID lotId, LotDtos.LotSplitRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        LotEntity source = requireLot(lotId, tenantId);
        if ("CLOSED".equals(source.status)) {
            throw new ConflictException("Lot is closed");
        }
        if (request.quantity() >= source.currentCount) {
            throw new ConflictException("Quantity must be less than the current headcount");
        }

        Instant now = Instant.now();
        LotEntity created = new LotEntity();
        created.id = UUID.randomUUID();
        created.tenantId = tenantId;
        created.farmId = source.farmId;
        created.speciesId = source.speciesId;
        created.name = request.name();
        created.productionType = source.productionType;
        created.status = "OPEN";
        created.entryDate = LocalDate.now();
        created.initialCount = request.quantity();
        created.currentCount = 0;
        created.origin = "SPLIT";
        created.unitCost = source.unitCost;
        created.createdAt = now;
        created.updatedAt = now;
        lotRepository.save(created);

        applyMovement(source, "TRANSFER", request.quantity(), now, "Split to " + created.id);
        applyMovement(created, "ENTRY", request.quantity(), now, "Split from " + source.id);
        auditService.record("LOT_SPLIT", "lot", created.id);
        return toResponse(created);
    }

    @Transactional
    public LotDtos.LotResponse merge(UUID lotId, UUID sourceLotId) {
        UUID tenantId = TenantContext.requireTenantId();
        LotEntity target = requireLot(lotId, tenantId);
        LotEntity source = requireLot(sourceLotId, tenantId);
        if (target.id.equals(source.id)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Cannot merge a lot with itself");
        }
        if ("CLOSED".equals(target.status) || "CLOSED".equals(source.status)) {
            throw new ConflictException("Lot is closed");
        }
        if (!target.farmId.equals(source.farmId) || !target.speciesId.equals(source.speciesId)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Lots must belong to the same farm and species");
        }
        if (source.currentCount == 0) {
            throw new ConflictException("Source lot is empty");
        }

        Instant now = Instant.now();
        int quantity = source.currentCount;
        applyMovement(source, "TRANSFER", quantity, now, "Merge into " + target.id);
        applyMovement(target, "ENTRY", quantity, now, "Merge from " + source.id);

        source.status = "CLOSED";
        source.closedAt = now;
        source.updatedAt = now;
        lotRepository.save(source);
        auditService.record("LOT_MERGED", "lot", target.id);
        return toResponse(target);
    }

    private LotMovementEntity applyMovement(LotEntity lot, String type, int quantity,
            Instant occurredAt, String reason) {
        if ("CLOSED".equals(lot.status)) {
            throw new ConflictException("Lot is closed");
        }
        if (INCREASE_TYPES.contains(type)) {
            lot.currentCount += quantity;
        } else {
            if (quantity > lot.currentCount) {
                throw new ConflictException("Insufficient lot headcount");
            }
            lot.currentCount -= quantity;
        }
        lot.updatedAt = Instant.now();
        lotRepository.save(lot);

        LotMovementEntity movement = new LotMovementEntity();
        movement.id = UUID.randomUUID();
        movement.tenantId = lot.tenantId;
        movement.lotId = lot.id;
        movement.type = type;
        movement.quantity = quantity;
        movement.occurredAt = occurredAt;
        movement.reason = reason;
        movement.actorId = AuthContext.userId();
        movement.createdAt = Instant.now();
        movementRepository.save(movement);
        auditService.record("LOT_MOVEMENT", "lot", lot.id);
        return movement;
    }

    private LotEntity requireLot(UUID lotId, UUID tenantId) {
        return lotRepository.findByIdAndTenantId(lotId, tenantId)
                .orElseThrow(() -> new NotFoundException("Lot not found"));
    }

    private void requireFarm(UUID farmId, UUID tenantId) {
        if (!farmRepository.existsByIdAndTenantId(farmId, tenantId)) {
            throw new NotFoundException("Farm not found");
        }
    }

    private void requireSpecies(UUID speciesId, UUID tenantId) {
        if (!speciesRepository.existsByIdAndTenantId(speciesId, tenantId)) {
            throw new NotFoundException("Species not found");
        }
    }

    private LotDtos.LotResponse toResponse(LotEntity lot) {
        return new LotDtos.LotResponse(lot.id, lot.farmId, lot.speciesId, lot.name, lot.code,
                lot.productionType, lot.status, lot.entryDate, lot.initialCount, lot.currentCount,
                lot.origin, lot.unitCost, lot.closedAt, lot.version, lot.createdAt, lot.updatedAt);
    }

    private LotDtos.LotMovementResponse toMovementResponse(LotMovementEntity movement) {
        return new LotDtos.LotMovementResponse(movement.id, movement.lotId, movement.type,
                movement.quantity, movement.occurredAt, movement.reason, movement.actorId,
                movement.createdAt);
    }

    private LotDtos.LotClosureResponse toClosureResponse(LotClosureEntity closure) {
        return new LotDtos.LotClosureResponse(closure.lotId, closure.mortalityRate,
                closure.feedConversion, closure.gmq, closure.cost, closure.revenue, closure.margin,
                closure.closedAt);
    }
}
