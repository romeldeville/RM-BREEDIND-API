package com.rmbreeding.api.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MfaSecretRepository extends JpaRepository<MfaSecretEntity, UUID> {

    Optional<MfaSecretEntity> findByUserId(UUID userId);
}
