package com.rmbreeding.api.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository extends JpaRepository<OtpCodeEntity, UUID> {

    Optional<OtpCodeEntity> findFirstByDestinationAndConsumedAtIsNullOrderByCreatedAtDesc(String destination);

    long countByDestinationAndCreatedAtAfter(String destination, Instant since);
}
