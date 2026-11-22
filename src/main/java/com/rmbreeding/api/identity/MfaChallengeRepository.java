package com.rmbreeding.api.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MfaChallengeRepository extends JpaRepository<MfaChallengeEntity, UUID> {

    Optional<MfaChallengeEntity> findByIdAndConsumedAtIsNull(UUID id);
}
