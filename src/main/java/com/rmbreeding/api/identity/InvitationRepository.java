package com.rmbreeding.api.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<InvitationEntity, UUID> {

    Optional<InvitationEntity> findByTokenHash(String tokenHash);

    List<InvitationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
