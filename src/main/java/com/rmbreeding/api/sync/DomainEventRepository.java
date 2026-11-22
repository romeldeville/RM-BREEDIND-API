package com.rmbreeding.api.sync;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DomainEventRepository extends JpaRepository<DomainEventEntity, UUID> {

    Optional<DomainEventEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<DomainEventEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByCorrectsEventId(UUID correctsEventId);

    boolean existsByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    List<DomainEventEntity> findByTenantIdAndServerSeqGreaterThanOrderByServerSeqAsc(
            UUID tenantId, long serverSeq, Pageable pageable);

    List<DomainEventEntity> findByTenantIdAndAggregateTypeAndAggregateIdOrderByOccurredAtAscServerSeqAsc(
            UUID tenantId, String aggregateType, UUID aggregateId);

    List<DomainEventEntity> findByTenantIdOrderByOccurredAtAscServerSeqAsc(UUID tenantId);

    long countByTenantIdAndServerSeqGreaterThan(UUID tenantId, long serverSeq);

    @Query("select coalesce(max(e.serverSeq), 0) from DomainEventEntity e where e.tenantId = :tenantId")
    long maxServerSeq(@Param("tenantId") UUID tenantId);
}
