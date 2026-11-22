package com.rmbreeding.api.reproduction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WeaningRepository extends JpaRepository<WeaningEntity, UUID> {

    List<WeaningEntity> findByTenantIdAndLitterIdOrderByWeanedAtDesc(UUID tenantId, UUID litterId);
}
