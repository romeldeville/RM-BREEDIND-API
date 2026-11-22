package com.rmbreeding.api.identity;

import jakarta.persistence.Column;
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
@Table(name = "invitations")
class InvitationEntity {

    @Id
    UUID id;

    UUID tenantId;

    @Column(columnDefinition = "citext")
    String email;

    String phone;

    String role;

    @JdbcTypeCode(SqlTypes.ARRAY)
    List<UUID> farmScope = new ArrayList<>();

    String tokenHash;

    Instant expiresAt;

    Instant acceptedAt;

    UUID invitedBy;

    Instant createdAt;
}
