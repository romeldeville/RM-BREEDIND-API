package com.rmbreeding.api.reporting;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dashboard_snapshots")
class DashboardSnapshotEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID farmId;

    long herdSize;

    long openAlerts;

    long birthsThisMonth;

    BigDecimal averageDailyGain;

    BigDecimal grossMargin;

    Instant refreshedAt;
}
