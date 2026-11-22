package com.rmbreeding.api.feeding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FeedReceiptRepository extends JpaRepository<FeedReceiptEntity, UUID> {

    List<FeedReceiptEntity> findByTenantIdAndFeedIdOrderByReceivedAtDesc(UUID tenantId, UUID feedId);

    List<FeedReceiptEntity> findByTenantIdAndExpiryDateLessThanEqualOrderByExpiryDateAsc(
            UUID tenantId, LocalDate before);
}
