package com.rmbreeding.api.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "memberships")
class MembershipEntity {

    @Id
    UUID id;

    UUID tenantId;

    UUID userId;

    String role;

    @JdbcTypeCode(SqlTypes.ARRAY)
    List<UUID> farmScope = new ArrayList<>();

    String status;

    Instant createdAt;

    Instant updatedAt;
}
