package com.rmbreeding.api.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncProjectionRepository extends JpaRepository<SyncProjectionEntity, UUID> {

    Optional<SyncProjectionEntity> findByTenantIdAndAggregateTypeAndAggregateId(
            UUID tenantId, String aggregateType, UUID aggregateId);

    List<SyncProjectionEntity> findByTenantIdAndAggregateTypeOrderByUpdatedAtDesc(
            UUID tenantId, String aggregateType);

    long countByTenantId(UUID tenantId);

    @Modifying
    @Query("delete from SyncProjectionEntity p where p.tenantId = :tenantId")
    int deleteAllForTenant(@Param("tenantId") UUID tenantId);
}
