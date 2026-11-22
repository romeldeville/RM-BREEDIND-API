package com.rmbreeding.api.feeding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeedStockRepository extends JpaRepository<FeedStockEntity, UUID> {

    Optional<FeedStockEntity> findByTenantIdAndFeedId(UUID tenantId, UUID feedId);

    List<FeedStockEntity> findByTenantId(UUID tenantId);
}
