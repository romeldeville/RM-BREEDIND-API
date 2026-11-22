package com.rmbreeding.api.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, UUID> {

    List<ExpenseEntity> findByTenantIdOrderByIncurredAtDesc(UUID tenantId);

    List<ExpenseEntity> findByTenantIdAndIncurredAtBetweenOrderByIncurredAtDesc(
            UUID tenantId, LocalDate from, LocalDate to);

    Optional<ExpenseEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
