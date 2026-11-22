package com.rmbreeding.api.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

    Optional<TenantEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query(value = "SELECT id FROM tenants", nativeQuery = true)
    List<UUID> findAllTenantIds();

    List<TenantEntity> findByStatusAndDeleteAfterBefore(String status, java.time.Instant deleteAfter);
}
