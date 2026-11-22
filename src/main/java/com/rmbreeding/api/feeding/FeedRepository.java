package com.rmbreeding.api.feeding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface FeedRepository extends JpaRepository<FeedEntity, UUID>,
        JpaSpecificationExecutor<FeedEntity> {

    Optional<FeedEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
