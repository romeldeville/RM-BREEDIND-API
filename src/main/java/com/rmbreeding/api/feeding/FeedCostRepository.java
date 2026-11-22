package com.rmbreeding.api.feeding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeedCostRepository extends JpaRepository<FeedCostEntity, UUID> {

    List<FeedCostEntity> findByTenantIdOrderByPeriodEndDesc(UUID tenantId);
}
