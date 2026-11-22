package com.rmbreeding.api.reporting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DashboardSnapshotRepository extends JpaRepository<DashboardSnapshotEntity, UUID> {

    Optional<DashboardSnapshotEntity> findFirstByTenantIdOrderByRefreshedAtDesc(UUID tenantId);

    @Query(value = "select id from tenants", nativeQuery = true)
    List<UUID> findAllTenantIds();
}
