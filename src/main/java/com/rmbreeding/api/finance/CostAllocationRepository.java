package com.rmbreeding.api.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CostAllocationRepository extends JpaRepository<CostAllocationEntity, UUID> {

    List<CostAllocationEntity> findByTenantIdAndLotIdOrderByAllocatedAtDesc(UUID tenantId, UUID lotId);

    List<CostAllocationEntity> findByTenantIdOrderByAllocatedAtDesc(UUID tenantId);
}
