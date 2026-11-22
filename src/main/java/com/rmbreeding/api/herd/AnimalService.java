package com.rmbreeding.api.herd;

import com.rmbreeding.api.identity.AuditService;
import com.rmbreeding.api.identity.AuthContext;
import com.rmbreeding.api.identity.FarmRepository;
import com.rmbreeding.api.identity.QuotaService;
import com.rmbreeding.api.referential.BreedRepository;
import com.rmbreeding.api.referential.PenRepository;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AnimalService {

    private static final Set<String> FINAL_STATUSES =
            Set.of("SOLD", "DEAD", "SLAUGHTERED", "TRANSFERRED", "ARCHIVED");

    private final AnimalRepository animalRepository;
    private final AnimalStatusHistoryRepository historyRepository;
    private final LotRepository lotRepository;
    private final LotService lotService;
    private final FarmRepository farmRepository;
    private final SpeciesRepository speciesRepository;
    private final BreedRepository breedRepository;
    private final PenRepository penRepository;
    private final QuotaService quotaService;
    private final AuditService auditService;

    public AnimalService(AnimalRepository animalRepository,
            AnimalStatusHistoryRepository historyRepository,
            LotRepository lotRepository,
            LotService lotService,
            FarmRepository farmRepository,
            SpeciesRepository speciesRepository,
            BreedRepository breedRepository,
            PenRepository penRepository,
            QuotaService quotaService,
            AuditService auditService) {
        this.animalRepository = animalRepository;
        this.historyRepository = historyRepository;
        this.lotRepository = lotRepository;
        this.lotService = lotService;
        this.farmRepository = farmRepository;
        this.speciesRepository = speciesRepository;
        this.breedRepository = breedRepository;
        this.penRepository = penRepository;
        this.quotaService = quotaService;
        this.auditService = auditService;
    }

    public long countActive() {
        UUID tenantId = TenantContext.requireTenantId();
        return animalRepository.countByTenantIdAndArchivedAtIsNull(tenantId);
    }

    public PageResponse<HerdDtos.AnimalResponse> list(String search, String status, UUID speciesId,
            UUID penId, UUID lotId, Integer limit, String cursor) {
        return list(search, status, speciesId, penId, lotId, null, limit, cursor);
    }

    public PageResponse<HerdDtos.AnimalResponse> list(String search, String status, UUID speciesId,
            UUID penId, UUID lotId, String sex, Integer limit, String cursor) {
        UUID tenantId = TenantContext.requireTenantId();
        int size = limit == null ? 50 : Math.max(1, Math.min(limit, 100));
        Cursor decoded = Cursor.decode(cursor);

        List<UUID> lotAnimalIds = lotId == null ? null : animalRepository.findAnimalIdsInLot(lotId);
        if (lotAnimalIds != null && lotAnimalIds.isEmpty()) {
            return PageResponse.last(List.of());
        }

        Specification<AnimalEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("tenantId"), tenantId));
            if (status != null && !status.isBlank()) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (speciesId != null) {
                predicates.add(builder.equal(root.get("speciesId"), speciesId));
            }
            if (sex != null && !sex.isBlank()) {
                predicates.add(builder.equal(root.get("sex"), sex));
            }
            if (penId != null) {
                predicates.add(builder.equal(root.get("penId"), penId));
            }
            if (lotAnimalIds != null) {
                predicates.add(root.get("id").in(lotAnimalIds));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("identifier")), pattern),
                        builder.like(builder.lower(root.get("name")), pattern)));
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

        Page<AnimalEntity> page = animalRepository.findAll(specification,
                PageRequest.of(0, size + 1, Sort.by(
                        Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        List<AnimalEntity> entities = new ArrayList<>(page.getContent());
        boolean hasMore = entities.size() > size;
        if (hasMore) {
            entities = new ArrayList<>(entities.subList(0, size));
        }
        String nextCursor = null;
        if (hasMore && !entities.isEmpty()) {
            AnimalEntity last = entities.getLast();
            nextCursor = new Cursor(last.createdAt, last.id).encode();
        }
        return new PageResponse<>(entities.stream().map(this::toResponse).toList(), nextCursor, hasMore);
    }

    public HerdDtos.AnimalResponse get(UUID animalId) {
        return toResponse(requireAnimal(animalId, TenantContext.requireTenantId()));
    }

    @Transactional
    public HerdDtos.AnimalResponse create(HerdDtos.AnimalCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        requireFarm(request.farmId(), tenantId);
        requireSpecies(request.speciesId(), tenantId);
        validateBreed(request.breedId(), request.speciesId(), tenantId);
        validatePen(request.penId(), request.farmId(), tenantId);
        if (animalRepository.existsByTenantIdAndFarmIdAndIdentifier(tenantId, request.farmId(),
                request.identifier())) {
            throw new ConflictException("Animal identifier already used on this farm");
        }
        validateParents(request.motherId(), request.fatherId(), null, request.farmId(), tenantId);

        long current = animalRepository.countByTenantId(tenantId);
        quotaService.requireCapacity(tenantId, "animals", current);

        Instant now = Instant.now();
        AnimalEntity animal = new AnimalEntity();
        animal.id = UUID.randomUUID();
        animal.tenantId = tenantId;
        animal.farmId = request.farmId();
        animal.speciesId = request.speciesId();
        animal.breedId = request.breedId();
        animal.penId = request.penId();
        animal.identifier = request.identifier();
        animal.name = request.name();
        animal.sex = request.sex();
        animal.birthDate = request.birthDate();
        animal.birthEstimated = Boolean.TRUE.equals(request.birthEstimated());
        animal.origin = request.origin();
        animal.status = "ACTIVE";
        animal.motherId = request.motherId();
        animal.fatherId = request.fatherId();
        animal.weightKg = request.weightKg();
        animal.acquiredAt = request.acquiredAt();
        animal.createdAt = now;
        animal.updatedAt = now;
        animalRepository.save(animal);
        recordStatusHistory(tenantId, animal.id, "ACTIVE", now, "Initial status");
        quotaService.setUsed(tenantId, "animals", (int) current + 1);
        auditService.record("ANIMAL_CREATED", "animal", animal.id);
        return toResponse(animal);
    }

    @Transactional
    public HerdDtos.AnimalResponse individualize(UUID lotId, LotDtos.IndividualizeRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        LotEntity lot = lotRepository.findByIdAndTenantId(lotId, tenantId)
                .orElseThrow(() -> new NotFoundException("Lot not found"));
        if (!"OPEN".equals(lot.status)) {
            throw new ConflictException("Lot is closed");
        }
        if (lot.currentCount < 1) {
            throw new ConflictException("Lot is empty");
        }
        if (animalRepository.existsByTenantIdAndFarmIdAndIdentifier(tenantId, lot.farmId,
                request.identifier())) {
            throw new ConflictException("Animal identifier already used on this farm");
        }
        long current = animalRepository.countByTenantId(tenantId);
        quotaService.requireCapacity(tenantId, "animals", current);

        Instant now = Instant.now();
        AnimalEntity animal = new AnimalEntity();
        animal.id = UUID.randomUUID();
        animal.tenantId = tenantId;
        animal.farmId = lot.farmId;
        animal.speciesId = lot.speciesId;
        animal.identifier = request.identifier();
        animal.sex = request.sex();
        animal.birthDate = request.birthDate();
        animal.birthEstimated = false;
        animal.origin = "LOT";
        animal.status = "ACTIVE";
        animal.createdAt = now;
        animal.updatedAt = now;
        animalRepository.save(animal);
        recordStatusHistory(tenantId, animal.id, "ACTIVE", now, "Individualized from lot");
        quotaService.setUsed(tenantId, "animals", (int) current + 1);

        lotService.move(lotId, new LotDtos.LotMovementRequest("INDIVIDUALIZATION", 1, now,
                "Individualized as " + request.identifier()));
        auditService.record("ANIMAL_INDIVIDUALIZED", "animal", animal.id);
        return toResponse(animal);
    }

    @Transactional
    public HerdDtos.AnimalResponse patch(UUID animalId, HerdDtos.AnimalPatchRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        AnimalEntity animal = requireAnimal(animalId, tenantId);
        if (FINAL_STATUSES.contains(animal.status)) {
            throw new ConflictException("Animal is in a final state");
        }
        if (request.identifier() != null && !request.identifier().equals(animal.identifier)) {
            if (animalRepository.existsByTenantIdAndFarmIdAndIdentifier(tenantId, animal.farmId,
                    request.identifier())) {
                throw new ConflictException("Animal identifier already used on this farm");
            }
            animal.identifier = request.identifier();
        }
        if (request.name() != null) {
            animal.name = request.name();
        }
        if (request.sex() != null) {
            animal.sex = request.sex();
        }
        if (request.breedId() != null) {
            validateBreed(request.breedId(), animal.speciesId, tenantId);
            animal.breedId = request.breedId();
        }
        if (request.penId() != null) {
            validatePen(request.penId(), animal.farmId, tenantId);
            animal.penId = request.penId();
        }
        if (request.birthDate() != null) {
            animal.birthDate = request.birthDate();
        }
        if (request.birthEstimated() != null) {
            animal.birthEstimated = request.birthEstimated();
        }
        if (request.origin() != null) {
            animal.origin = request.origin();
        }
        if (request.weightKg() != null) {
            animal.weightKg = request.weightKg();
        }
        if (request.acquiredAt() != null) {
            animal.acquiredAt = request.acquiredAt();
        }
        UUID motherId = request.motherId() == null ? animal.motherId : request.motherId();
        UUID fatherId = request.fatherId() == null ? animal.fatherId : request.fatherId();
        validateParents(motherId, fatherId, animal.id, animal.farmId, tenantId);
        animal.motherId = motherId;
        animal.fatherId = fatherId;
        animal.updatedAt = Instant.now();
        animalRepository.save(animal);
        auditService.record("ANIMAL_UPDATED", "animal", animal.id);
        return toResponse(animal);
    }

    @Transactional
    public HerdDtos.AnimalStatusResponse changeStatus(UUID animalId, HerdDtos.AnimalStatusRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        AnimalEntity animal = requireAnimal(animalId, tenantId);
        if (FINAL_STATUSES.contains(animal.status) && !animal.status.equals(request.status())) {
            throw new ConflictException("Animal is in a final state");
        }
        Instant occurredAt = request.occurredAt() == null ? Instant.now() : request.occurredAt();
        if (!animal.status.equals(request.status())) {
            animal.status = request.status();
            animal.updatedAt = Instant.now();
            if ("ARCHIVED".equals(request.status())) {
                animal.archivedAt = animal.updatedAt;
            }
            animalRepository.save(animal);
        }
        AnimalStatusHistoryEntity history = recordStatusHistory(tenantId, animal.id, request.status(),
                occurredAt, request.reason());
        auditService.record("ANIMAL_STATUS_CHANGED", "animal", animal.id);
        return toStatusResponse(history);
    }

    public List<HerdDtos.AnimalStatusResponse> statusHistory(UUID animalId) {
        UUID tenantId = TenantContext.requireTenantId();
        requireAnimal(animalId, tenantId);
        return historyRepository.findByTenantIdAndAnimalIdOrderByChangedAtDesc(tenantId, animalId)
                .stream().map(this::toStatusResponse).toList();
    }

    private AnimalStatusHistoryEntity recordStatusHistory(UUID tenantId, UUID animalId, String status,
            Instant changedAt, String reason) {
        AnimalStatusHistoryEntity history = new AnimalStatusHistoryEntity();
        history.id = UUID.randomUUID();
        history.tenantId = tenantId;
        history.animalId = animalId;
        history.status = status;
        history.changedAt = changedAt;
        history.reason = reason;
        history.actorId = AuthContext.userId();
        historyRepository.save(history);
        return history;
    }

    private AnimalEntity requireAnimal(UUID animalId, UUID tenantId) {
        return animalRepository.findByIdAndTenantId(animalId, tenantId)
                .orElseThrow(() -> new NotFoundException("Animal not found"));
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

    private void validateBreed(UUID breedId, UUID speciesId, UUID tenantId) {
        if (breedId != null && !breedRepository.existsByIdAndTenantIdAndSpeciesId(breedId, tenantId, speciesId)) {
            throw new NotFoundException("Breed not found for this species");
        }
    }

    private void validatePen(UUID penId, UUID farmId, UUID tenantId) {
        if (penId != null && !penRepository.existsByIdAndTenantIdAndFarmId(penId, tenantId, farmId)) {
            throw new NotFoundException("Pen not found for this farm");
        }
    }

    private void validateParents(UUID motherId, UUID fatherId, UUID animalId, UUID farmId, UUID tenantId) {
        if (motherId != null && motherId.equals(fatherId)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Mother and father must differ");
        }
        if (motherId != null) {
            if (motherId.equals(animalId)) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "An animal cannot be its own parent");
            }
            AnimalEntity mother = requireAnimal(motherId, tenantId);
            if (!"FEMALE".equals(mother.sex)) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Mother must be female");
            }
            if (!mother.farmId.equals(farmId)) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Mother must belong to the same farm");
            }
        }
        if (fatherId != null) {
            if (fatherId.equals(animalId)) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "An animal cannot be its own parent");
            }
            AnimalEntity father = requireAnimal(fatherId, tenantId);
            if (!"MALE".equals(father.sex)) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Father must be male");
            }
            if (!father.farmId.equals(farmId)) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Father must belong to the same farm");
            }
        }
    }

    private HerdDtos.AnimalResponse toResponse(AnimalEntity animal) {
        return new HerdDtos.AnimalResponse(animal.id, animal.farmId, animal.speciesId, animal.breedId,
                animal.penId, animal.identifier, animal.name, animal.sex, animal.birthDate,
                animal.birthEstimated, animal.origin, animal.status, animal.motherId, animal.fatherId,
                animal.photoKey, animal.weightKg, animal.acquiredAt, animal.archivedAt, animal.createdAt,
                animal.updatedAt, animal.version);
    }

    private HerdDtos.AnimalStatusResponse toStatusResponse(AnimalStatusHistoryEntity history) {
        return new HerdDtos.AnimalStatusResponse(history.id, history.animalId, history.status,
                history.changedAt, history.reason, history.actorId);
    }
}
