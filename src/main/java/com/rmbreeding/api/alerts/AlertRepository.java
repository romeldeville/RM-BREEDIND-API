package com.rmbreeding.api.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<AlertEntity, UUID>, JpaSpecificationExecutor<AlertEntity> {

    Optional<AlertEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<AlertEntity> findByTenantIdAndDedupKey(UUID tenantId, String dedupKey);

    List<AlertEntity> findByTenantIdAndStatusInOrderByDueDateAsc(UUID tenantId, Collection<String> statuses);

    List<AlertEntity> findByTenantIdAndStatusInAndDueDateLessThanEqualOrderByPriorityAscDueDateAsc(
            UUID tenantId, Collection<String> statuses, java.time.LocalDate dueDate);

    List<AlertEntity> findByTenantIdAndEntityTypeAndEntityIdAndStatusIn(
            UUID tenantId, String entityType, UUID entityId, Collection<String> statuses);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    @Query(value = "SELECT id FROM tenants", nativeQuery = true)
    List<UUID> findAllTenantIds();
}
