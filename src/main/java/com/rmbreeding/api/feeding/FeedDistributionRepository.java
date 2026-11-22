package com.rmbreeding.api.feeding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FeedDistributionRepository extends JpaRepository<FeedDistributionEntity, UUID> {

    List<FeedDistributionEntity> findByTenantIdOrderByDistributedAtDesc(UUID tenantId);

    List<FeedDistributionEntity> findByTenantIdAndFeedIdOrderByDistributedAtDesc(UUID tenantId, UUID feedId);

    List<FeedDistributionEntity> findByTenantIdAndLotIdOrderByDistributedAtDesc(UUID tenantId, UUID lotId);

    List<FeedDistributionEntity> findByTenantIdAndLotIdAndDistributedAtBetweenOrderByDistributedAtDesc(
            UUID tenantId, UUID lotId, Instant from, Instant to);

    List<FeedDistributionEntity> findByTenantIdAndAnimalIdAndDistributedAtBetweenOrderByDistributedAtDesc(
            UUID tenantId, UUID animalId, Instant from, Instant to);
}
