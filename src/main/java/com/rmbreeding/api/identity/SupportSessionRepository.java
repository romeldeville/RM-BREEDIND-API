package com.rmbreeding.api.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportSessionRepository extends JpaRepository<SupportSessionEntity, UUID> {

    List<SupportSessionEntity> findByTenantIdOrderByStartedAtDesc(UUID tenantId);
}
