package com.rmbreeding.api.alerts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    Optional<TaskEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<TaskEntity> findByTenantIdOrderByDueDateAsc(UUID tenantId);

    List<TaskEntity> findByTenantIdAndStatusInOrderByDueDateAsc(UUID tenantId, Collection<String> statuses);

    List<TaskEntity> findByTenantIdAndStatusInAndDueDateLessThanEqualOrderByDueDateAsc(
            UUID tenantId, Collection<String> statuses, java.time.LocalDate dueDate);
}
