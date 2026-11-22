package com.rmbreeding.api.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RevenueRepository extends JpaRepository<RevenueEntity, UUID> {

    List<RevenueEntity> findByTenantIdOrderByReceivedAtDesc(UUID tenantId);

    List<RevenueEntity> findByTenantIdAndReceivedAtBetweenOrderByReceivedAtDesc(
            UUID tenantId, LocalDate from, LocalDate to);
}
