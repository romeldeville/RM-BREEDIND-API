package com.rmbreeding.api.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<MembershipEntity, UUID> {

    List<MembershipEntity> findByUserId(UUID userId);

    List<MembershipEntity> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);

    Optional<MembershipEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserId(UUID tenantId, UUID userId);

    long countByTenantId(UUID tenantId);
}
